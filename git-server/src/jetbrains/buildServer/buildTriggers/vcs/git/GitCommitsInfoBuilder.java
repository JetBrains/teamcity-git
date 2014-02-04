/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleResolverImpl;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static jetbrains.buildServer.buildTriggers.vcs.git.Constants.VCS_NAME;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.getObjectId;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.isTag;

public class GitCommitsInfoBuilder implements CommitsInfoBuilder, GitServerExtension {

  private final GitVcsSupport myVcs;

  public GitCommitsInfoBuilder(@NotNull GitVcsSupport vcs) {
    myVcs = vcs;
    myVcs.addExtension(this);
  }

  public void collectCommits(@NotNull final VcsRoot root,
                             @NotNull final CheckoutRules rules,
                             @NotNull final CommitsConsumer consumer) throws VcsException {

    OperationContext ctx = myVcs.createContext(root, "collecting commits");
    try {
      GitVcsRoot gitRoot = makeRootWithTags(ctx, root);
      Repository db = ctx.getRepository();
      myVcs.getCollectChangesPolicy().ensureRepositoryStateLoadedFor(ctx, db, false, myVcs.getCurrentState(gitRoot));

      final Map<String,Ref> currentState = ctx.getRepository().getAllRefs();
      RevWalk walk = new RevWalk(db);
      for (Ref tip : new HashSet<Ref>(currentState.values())) {
        try {
          RevObject obj = walk.parseAny(getObjectId(tip));
          if (obj instanceof RevCommit)
            walk.markStart((RevCommit) obj);
        } catch (MissingObjectException e) {
          //log
        } catch (IOException e) {
          //log
        }
      }

      Map<String, Set<String>> index = getCommitToRefIndex(currentState);
      RevCommit c;
      while ((c = walk.next()) != null) {
        final CommitDataBean commit = new CommitDataBean(c.getId().getName(), c.getId().getName(), c.getAuthorIdent().getWhen());
        commit.setCommitAuthor(GitServerUtil.getUser(gitRoot, c));
        commit.setCommitMessage(c.getFullMessage());
        for (RevCommit p : c.getParents()) {
          commit.addParentRevision(p.getId().getName());
        }

        SubmoduleWalk sw = new SubmoduleWalk(db);
        sw.setRootTree(c.getTree());
        sw.setTree(c.getTree());
        sw = sw.loadModulesConfig();
        try {
          while(sw.next()) {
            final String path = sw.getPath();
            final ObjectId rev = sw.getObjectId();
            final String url = sw.getModulesUrl();

            if (path == null || rev == null || url == null) continue;
            String resolvedUrl = SubmoduleResolverImpl.resolveSubmoduleUrl(db, url);

            commit.addMountPoint(new CommitMountPointDataBean(
              VCS_NAME,
              resolvedUrl,
              path,
              rev.getName()
            ));
          }
        } finally {
          sw.release();
        }

        Set<String> refs = index.get(commit.getVersion());
        if (refs != null) {
          for (String ref : refs) {
            if (isTag(ref)) {
              commit.addTag(ref);
            } else {
              commit.addBranch(ref);
              commit.addHead(ref);
            }
          }
        }

        consumer.consumeCommit(commit);
      }
    } catch (Exception e) {
      throw new VcsException(e);
    } finally {
      ctx.close();
    }
  }

  @NotNull
  private GitVcsRoot makeRootWithTags(@NotNull OperationContext ctx, @NotNull VcsRoot root) throws VcsException {
    Map<String, String> params = new HashMap<String, String>(root.getProperties());
    params.put(Constants.REPORT_TAG_REVISIONS, "true");
    return ctx.getGitRoot(new VcsRootImpl(root.getId(), params));
  }


  @NotNull
  private Map<String, Set<String>> getCommitToRefIndex(@NotNull Map<String, Ref> state) {
    Map<String, Set<String>> index = new HashMap<String, Set<String>>();
    for (Map.Entry<String, Ref> e : state.entrySet()) {
      String ref = e.getKey();
      final String commit = GitUtils.getRevision(e.getValue());
      Set<String> refs = index.get(commit);
      if (refs == null) {
        refs = new HashSet<String>();
        index.put(commit, refs);
      }
      refs.add(ref);
    }
    return index;
  }

}
