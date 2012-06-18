/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.impl.LogUtil;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
* @author dmitry.neverov
*/
class GitCollectChangesPolicy implements CollectChangesBetweenRoots {

  private static final Logger LOG = Logger.getInstance(GitCollectChangesPolicy.class.getName());

  private final GitVcsSupport myVcs;
  private final ServerPluginConfig myConfig;

  public GitCollectChangesPolicy(@NotNull GitVcsSupport vcs,
                                 @NotNull ServerPluginConfig config) {
    myVcs = vcs;
    myConfig = config;
  }


  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot fromRoot,
                                               @NotNull String fromVersion,
                                               @NotNull VcsRoot toRoot,
                                               @Nullable String toVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    logCollectChanges(fromRoot, fromVersion, toRoot, toVersion);
    if (toVersion == null) {
      LOG.warn("Version of root " + LogUtil.describe(toRoot) + " is null, return empty list of changes");
      return Collections.emptyList();
    }
    String forkPoint = getLastCommonVersion(fromRoot, fromVersion, toRoot, toVersion);
    return collectChanges(toRoot, forkPoint, toVersion, checkoutRules);
  }

  @NotNull
  public List<ModificationData> collectChanges(@NotNull VcsRoot root,
                                               @NotNull String fromVersion,
                                               @Nullable String currentVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    List<ModificationData> result = new ArrayList<ModificationData>();
    OperationContext context = myVcs.createContext(root, "collecting changes");
    try {
      logCollectChanges(fromVersion, currentVersion, context);
      if (currentVersion == null) {
        LOG.warn("Current version is null for " + context.getGitRoot().debugInfo() + ", return empty list of changes");
        return result;
      }
      String upperBoundSHA = GitUtils.versionRevision(currentVersion);
      myVcs.ensureCommitLoaded(context, context.getGitRoot(), upperBoundSHA);
      String lowerBoundSHA = GitUtils.versionRevision(fromVersion);
      Repository r = context.getRepository();
      result.addAll(getModifications(context, r, upperBoundSHA, lowerBoundSHA));
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
    return result;
  }

  private String getLastCommonVersion(@NotNull VcsRoot baseRoot,
                                      @NotNull String baseVersion,
                                      @NotNull VcsRoot tipRoot,
                                      @NotNull String tipVersion) throws VcsException {
    OperationContext context = myVcs.createContext(tipRoot, "find fork version");
    GitVcsRoot baseGitRoot = context.getGitRoot(baseRoot);
    GitVcsRoot tipGitRoot = context.getGitRoot();
    logFindLastCommonAncestor(baseVersion, tipVersion, baseGitRoot, tipGitRoot);
    RevWalk walk = null;
    try {
      RevCommit baseCommit = myVcs.ensureCommitLoaded(context, baseGitRoot, baseVersion);
      RevCommit tipCommit = myVcs.ensureCommitLoaded(context, tipGitRoot, tipVersion);
      Repository tipRepository = context.getRepository(tipGitRoot);
      walk = new RevWalk(tipRepository);
      walk.setRevFilter(RevFilter.MERGE_BASE);
      walk.markStart(walk.parseCommit(baseCommit.getId()));
      walk.markStart(walk.parseCommit(tipCommit.getId()));
      final RevCommit base = walk.next();
      String result = base.getId().name();
      logLastCommonAncestor(baseGitRoot, tipGitRoot, result);
      return result;
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      try {
        if (walk != null)
          walk.release();
      } finally {
        context.close();
      }
    }
  }

  private List<ModificationData> getModifications(@NotNull final OperationContext context,
                                                  @NotNull final Repository r,
                                                  @NotNull final String upperBoundSHA,
                                                  @NotNull final String lowerBoundSHA) throws VcsException, IOException {
    List<ModificationData> modifications = new ArrayList<ModificationData>();
    ModificationDataRevWalk revWalk = new ModificationDataRevWalk(context, myConfig.getFixedSubmoduleCommitSearchDepth());
    revWalk.sort(RevSort.TOPO);
    try {
      revWalk.markStart(revWalk.parseCommit(ObjectId.fromString(upperBoundSHA)));
      ObjectId lowerBoundId = ObjectId.fromString(lowerBoundSHA);
      if (r.hasObject(lowerBoundId)) {
        revWalk.markUninteresting(revWalk.parseCommit(lowerBoundId));
      } else {
        logFromRevisionNotFound(lowerBoundSHA);
        revWalk.limitByNumberOfCommits(myConfig.getNumberOfCommitsWhenFromVersionNotFound());
      }
      while (revWalk.next() != null) {
        modifications.add(revWalk.createModificationData());
      }
      return modifications;
    } finally {
      revWalk.release();
    }
  }

  private void logCollectChanges(@NotNull VcsRoot fromRoot,
                                 @NotNull String fromVersion,
                                 @NotNull VcsRoot toRoot,
                                 @Nullable String toVersion) {
    LOG.debug("Collecting changes [" + LogUtil.describe(fromRoot) + "-" + fromVersion+ "].." +
              "[" + LogUtil.describe(toRoot) + "-" + toVersion + "]");
  }

  private void logCollectChanges(@NotNull String fromVersion,
                                 @Nullable String currentVersion,
                                 @NotNull OperationContext context) throws VcsException {
    LOG.debug("Collecting changes " + fromVersion + ".." + currentVersion + " for " + context.getGitRoot().debugInfo());
  }

  private void logFindLastCommonAncestor(@NotNull String baseVersion,
                                         @NotNull String tipVersion,
                                         @NotNull GitVcsRoot baseGitRoot,
                                         @NotNull GitVcsRoot tipGitRoot) {
    LOG.debug("Find last common version between [" + baseGitRoot.debugInfo() + "-" + baseVersion + "].." +
              "[" + tipGitRoot.debugInfo() + "-" + tipVersion + "]");
  }

  private void logLastCommonAncestor(@NotNull GitVcsRoot baseGitRoot,
                                     @NotNull GitVcsRoot tipGitRoot,
                                     @NotNull String ancestor) {
    LOG.debug("Last common revision between " + baseGitRoot.debugInfo() + " and " + tipGitRoot.debugInfo() + " is " + ancestor);
  }

  private void logFromRevisionNotFound(@NotNull String lowerBoundSHA) {
    LOG.warn("From version " + lowerBoundSHA + " is not found, collect last " +
             myConfig.getNumberOfCommitsWhenFromVersionNotFound() + " commits");
  }
}
