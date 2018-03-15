/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.Submodule;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleResolverImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmodulesConfig;
import jetbrains.buildServer.vcs.*;
import org.apache.log4j.Logger;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.getAuthorIdent;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.getFullUserName;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.isTag;

public class GitCommitsInfoBuilder implements CommitsInfoBuilder, GitServerExtension {
  private static final Logger LOG = Logger.getLogger(GitCommitsInfoBuilder.class.getName());

  private final GitVcsSupport myVcs;
  private final GitFetchService myFetchService;

  public GitCommitsInfoBuilder(@NotNull GitVcsSupport vcs, @NotNull GitFetchService fetchService) {
    myVcs = vcs;
    myFetchService = fetchService;
    myVcs.addExtension(this);
  }

  public void collectCommits(@NotNull final VcsRoot root,
                             @NotNull final CheckoutRules rules,
                             @NotNull final CommitsConsumer consumer) throws VcsException {
    final OperationContext ctx = myVcs.createContext(root, "collecting commits");
    GitVcsRoot gitRoot = ctx.getGitRoot();
    myVcs.getRepositoryManager().runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      try {
        //fetch service is called before, so we may re-use results of it to avoid extra CPU waste
        final RepositoryStateData currentStateWithTags = myFetchService.getOrCreateRepositoryState(ctx);

        collect(ctx, ctx.getRepository(), consumer, currentStateWithTags.getBranchRevisions(), gitRoot.isIncludeCommitInfoSubmodules());
      } catch (Exception e) {
        throw new VcsException(e);
      } finally {
        ctx.close();
      }
    });
  }

  private void collect(@NotNull OperationContext context,
                       @NotNull final Repository db,
                       @NotNull final CommitsConsumer consumer,
                       @NotNull final Map<String, String> currentStateWithTags,
                       final boolean includeSubmodules) throws IOException {

    final ObjectDatabase cached = db.getObjectDatabase().newCachedDatabase();
    final Map<String, Set<String>> index = getCommitToRefIndex(currentStateWithTags);

    final DotGitModulesResolver resolver = new CachedDotGitModulesResolver(new DotGitModulesResolverImpl(db));
    final CommitTreeProcessor proc = new CommitTreeProcessor(resolver, db);

    final RevWalk walk = new RevWalk(cached.newReader());

    try {
      initWalk(walk, currentStateWithTags);
      RevCommit c;
      while ((c = walk.next()) != null) {
        final CommitDataBean commit = createCommit(c);

        includeRefs(index, commit);

        if (includeSubmodules) {
          includeSubModules(context, db, proc, c, commit);
        }

        consumer.consumeCommit(commit);
      }
    } finally {
      walk.dispose();
    }
  }

  private void includeSubModules(@NotNull final OperationContext context,
                                 @NotNull final Repository db,
                                 @NotNull final CommitTreeProcessor proc,
                                 @NotNull final RevCommit commit,
                                 @NotNull final CommitDataBean bean) {
    final SubInfo tree = proc.processCommitTree(commit);
    if (tree == null) return;

    final SubmodulesConfig config = tree.getConfig();
    final Map<String,AnyObjectId> modules = tree.getSubmoduleToPath();

    for (Submodule sub : config.getSubmodules()) {
      final AnyObjectId mountedCommit = modules.get(sub.getPath());

      if (mountedCommit == null) continue;

      final String url;
      try {
        url = SubmoduleResolverImpl.resolveSubmoduleUrl(context.getPluginConfig(), db, sub.getUrl());
      } catch (URISyntaxException e) {
        continue;
      }

      bean.addMountPoint(new CommitMountPointDataBean(Constants.VCS_NAME, url, sub.getPath(), mountedCommit.name()));
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

  @NotNull
  private CommitDataBean createCommit(@NotNull final RevCommit c) {
    final String id = c.getId().getName();
    final RevCommit[] parents = c.getParents();

    CommitDataBean commit;

    try {
      final PersonIdent authorIdent = getAuthorIdent(c);
      commit = new CommitDataBean(id, id, authorIdent.getWhen());
      commit.setCommitAuthor(getFullUserName(authorIdent));
      commit.setCommitMessage(GitServerUtil.getFullMessage(c));
    } catch (Throwable t) {
      LOG.debug("Failed to read commit author or message for " + id + ". " + t.getMessage(), t);
      commit = new CommitDataBean(id, id, new Date(/*11 aug 1984*/461062365000L));
      commit.setCommitAuthor("unknown user");
      commit.setCommitMessage("No description provided");
    }

    for (RevCommit p : parents) {
      commit.addParentRevision(p.getId().getName());
    }
    return commit;
  }

  private void initWalk(@NotNull final RevWalk walk,
                        @NotNull final Map<String, String> currentState) {
    walk.sort(RevSort.TOPO);

    for (String tip : new HashSet<String>(currentState.values())) {
      try {
        final RevObject obj = walk.parseAny(ObjectId.fromString(tip));
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
  private Map<String, Set<String>> getCommitToRefIndex(@NotNull final Map<String, String> state) {
    final Map<String, Set<String>> index = new HashMap<String, Set<String>>();
    for (Map.Entry<String, String> e : state.entrySet()) {
      final String ref = e.getKey();
      final String commit = e.getValue();
      Set<String> refs = index.get(commit);
      if (refs == null) {
        refs = new HashSet<String>(1);
        index.put(commit, refs);
      }
      refs.add(ref);
    }
    return index;
  }
}
