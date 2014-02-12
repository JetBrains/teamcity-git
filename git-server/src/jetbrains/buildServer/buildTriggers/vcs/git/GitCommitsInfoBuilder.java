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
import org.eclipse.jgit.errors.ConfigInvalidException;
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
import java.net.URISyntaxException;
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

    final OperationContext ctx = myVcs.createContext(root, "collecting commits");
    try {
      final GitVcsRoot gitRoot = makeRootWithTags(ctx, root);
      final Repository db = ctx.getRepository();

      //fetch repo if needed
      myVcs.getCollectChangesPolicy().ensureRepositoryStateLoadedFor(ctx, db, false, myVcs.getCurrentState(gitRoot));

      final Map<String,Ref> currentState = ctx.getRepository().getAllRefs();
      final RevWalk walk = new RevWalk(db);
      try {
        initWalk(walk, currentState);
        iterateCommits(gitRoot, db, walk, getCommitToRefIndex(currentState), consumer);
      } finally {
        walk.dispose();
      }
    } catch (Exception e) {
      throw new VcsException(e);
    } finally {
      ctx.close();
    }
  }

  private void iterateCommits(@NotNull final GitVcsRoot gitRoot,
                              @NotNull final Repository db,
                              @NotNull final RevWalk walk,
                              @NotNull final Map<String, Set<String>> refIndex,
                              @NotNull final CommitsConsumer consumer) throws IOException, ConfigInvalidException, URISyntaxException {

    RevCommit c;
    while ((c = walk.next()) != null) {
      final CommitDataBean commit = createCommit(gitRoot, c);
      walkSubmodules(db, c, commit);
      includeRefs(refIndex, commit);

      consumer.consumeCommit(commit);
    }
  }

  private void includeRefs(@NotNull final Map<String, Set<String>> refIndex,
                           @NotNull final CommitDataBean commit) {
    Set<String> refs = refIndex.get(commit.getVersion());
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
  }

  private void walkSubmodules(@NotNull final Repository db,
                              @NotNull final RevCommit c,
                              @NotNull final CommitDataBean commit)
    throws IOException, ConfigInvalidException, URISyntaxException {
    SubmoduleWalk sw = new SubmoduleWalk(db);

    sw.setRootTree(c.getTree());
    sw.setTree(c.getTree());
    sw = sw.loadModulesConfig();

    try {
      while(sw.next()) {
        includeSubmodule(db, commit, sw);
      }
    } finally {
      sw.release();
    }
  }

  private void includeSubmodule(@NotNull final Repository db,
                                @NotNull final CommitDataBean commit,
                                @NotNull final SubmoduleWalk sw) throws IOException, ConfigInvalidException, URISyntaxException {
    final String path = sw.getPath();
    final ObjectId rev = sw.getObjectId();
    final String url = sw.getModulesUrl();

    if (path == null || rev == null || url == null) return;
    final String resolvedUrl = SubmoduleResolverImpl.resolveSubmoduleUrl(db, url);

    commit.addMountPoint(new CommitMountPointDataBean(
      VCS_NAME,
      resolvedUrl,
      path,
      rev.getName()
    ));
  }

  @NotNull
  private CommitDataBean createCommit(@NotNull final GitVcsRoot gitRoot,
                                      @NotNull final RevCommit c) {
    final CommitDataBean commit = new CommitDataBean(c.getId().getName(), c.getId().getName(), c.getAuthorIdent().getWhen());
    commit.setCommitAuthor(GitServerUtil.getUser(gitRoot, c));
    commit.setCommitMessage(c.getFullMessage());
    for (RevCommit p : c.getParents()) {
      commit.addParentRevision(p.getId().getName());
    }
    return commit;
  }

  private void initWalk(@NotNull final RevWalk walk,
                        @NotNull final Map<String, Ref> currentState) {
    for (Ref tip : new HashSet<Ref>(currentState.values())) {
      try {
        final RevObject obj = walk.parseAny(getObjectId(tip));

        if (obj instanceof RevCommit) {
          walk.markStart((RevCommit) obj);
        }
      } catch (MissingObjectException e) {
        //log
      } catch (IOException e) {
        //log
      }
    }
  }

  @NotNull
  private GitVcsRoot makeRootWithTags(@NotNull final OperationContext ctx, @NotNull final VcsRoot root) throws VcsException {
    Map<String, String> params = new HashMap<String, String>(root.getProperties());
    params.put(Constants.REPORT_TAG_REVISIONS, "true");
    return ctx.getGitRoot(new VcsRootImpl(root.getId(), params));
  }

  @NotNull
  private Map<String, Set<String>> getCommitToRefIndex(@NotNull final Map<String, Ref> state) {
    final Map<String, Set<String>> index = new HashMap<String, Set<String>>();
    for (Map.Entry<String, Ref> e : state.entrySet()) {
      final String ref = e.getKey();
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
