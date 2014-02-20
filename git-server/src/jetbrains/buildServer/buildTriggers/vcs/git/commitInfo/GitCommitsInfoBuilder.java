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

package jetbrains.buildServer.buildTriggers.vcs.git.commitInfo;

import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.getFullUserName;
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
      final Repository db = ctx.getRepository();

      myVcs.getCollectChangesPolicy().ensureRepositoryStateLoadedFor(ctx, db, false, myVcs.getCurrentState(makeRootWithTags(ctx, root)));

      collectRevs(db, consumer);
    } catch (Exception e) {
      throw new VcsException(e);
    } finally {
      ctx.close();
    }
  }

  private void collectRevs(@NotNull final Repository db,
                           @NotNull final CommitsConsumer consumer) throws IOException {
    final Map<String,Ref> currentState = db.getAllRefs();
    final Map<String, Set<String>> index = getCommitToRefIndex(currentState);
    final RevWalk walk = new RevWalk(db);

    try {
      initWalk(walk, currentState);
      RevCommit c;
      while ((c = walk.next()) != null) {
        processCommit(index, c, consumer);
      }
    } finally {
      walk.dispose();
    }
  }

  private void processCommit(@NotNull final Map<String, Set<String>> refIndex,
                             @NotNull final RevCommit c, @NotNull final CommitsConsumer consumer) {
    final CommitDataBean commit = createCommit(c);
    includeRefs(refIndex, commit);

    consumer.consumeCommit(commit);
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

  @NotNull
  private CommitDataBean createCommit(@NotNull final RevCommit c) {
    final PersonIdent authorIdent = c.getAuthorIdent();
    final CommitDataBean commit = new CommitDataBean(c.getId().getName(), c.getId().getName(), authorIdent.getWhen());

    commit.setCommitAuthor(getFullUserName(authorIdent));
    commit.setCommitMessage(c.getFullMessage());
    for (RevCommit p : c.getParents()) {
      commit.addParentRevision(p.getId().getName());
    }
    return commit;
  }

  private void initWalk(@NotNull final RevWalk walk,
                        @NotNull final Map<String, Ref> currentState) {
    walk.sort(RevSort.TOPO);

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
