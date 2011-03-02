/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
* @author kir
*/
class GitMapFullPath {

  private static final Logger LOG = Logger.getInstance(GitMapFullPath.class.getName());

  private static final RevisionsCache ourCache = new RevisionsCache();

  private final GitVcsSupport myGitSupport;
  private final VcsRootEntry myRootEntry;
  private final String myFullPath;

  private OperationContext myContext;
  private final Settings mySettings;
  private final int myFirstSep;
  private final int myLastSep;

  private String myGitRevision;

  public GitMapFullPath(final OperationContext context, final GitVcsSupport gitSupport, final VcsRootEntry rootEntry, final String fullPath) throws VcsException {
    myGitSupport = gitSupport;
    myRootEntry = rootEntry;
    myFullPath = fullPath;

    myFirstSep = myFullPath.indexOf("|");
    myLastSep = myFullPath.lastIndexOf("|");
    myContext = context;
    mySettings = context.getSettings();
  }

  public Collection<String> mapFullPath() throws VcsException {
    if (invalidFormat())
      return Collections.emptySet();

    initRevision();

    if (fullPathHasRevision()) {
      if (!repositoryContainsRevision()) return Collections.emptySet();
    } else {
      if (!matchRepositoryByUrl(repositoryUrlWithBranch())) return Collections.emptySet();
    }

    return returnPathAsMapped();
  }

  private void initRevision() {
    myGitRevision = myFullPath.substring(0, myFirstSep).trim();
  }

  private Collection<String> returnPathAsMapped() {
    final String path = myFullPath.substring(myLastSep + 1).trim();
    return Collections.singleton(path);
  }


  private boolean repositoryContainsRevision() throws VcsException {
    RepositoryRevisionCache repositoryCache = ourCache.getRepositoryCache(myRootEntry.getVcsRoot());
    Boolean result = repositoryCache.hasRevision(myGitRevision);
    if (result != null) {
      return result;
    } else {
      result = findCommit() != null;
      repositoryCache.saveRevision(myGitRevision, result);
      return result;
    }
  }


  /**
   * @return revCommit or null if repository has not such commit
   */
  @Nullable
  private RevCommit findCommit() throws VcsException {
    final Repository repository = myContext.getRepository();
    try {
      return myGitSupport.getCommit(repository, myGitRevision);
    } catch (IOException e) {
      return null;
    }
  }


  private String repositoryUrlWithBranch() {
    return myFullPath.substring(myFirstSep + 1, myLastSep).trim();
  }

  private boolean fullPathHasRevision() {
    return myGitRevision.length() > 0;
  }

  private boolean invalidFormat() {
    return myFirstSep < 0 || myLastSep == myFirstSep;
  }

  private boolean matchRepositoryByUrl(@NotNull final String repositoryUrlWithBranch) {
    final int branchSep = repositoryUrlWithBranch.indexOf("#");

    final URIish url;
    final String branch;

    if (branchSep < 0) {
      try {
        url = new URIish(repositoryUrlWithBranch);
      } catch (final URISyntaxException e) {
        LOG.error(e);
        return false;
      }
      branch = null;
    }
    else {
      try {
        url = new URIish(repositoryUrlWithBranch.substring(0, branchSep).trim());
      } catch (final URISyntaxException e) {
        LOG.error(e);
        return false;
      }
      branch = getNullIfEmpty(repositoryUrlWithBranch.substring(branchSep + 1));
    }

    final URIish settingsUrl = mySettings.getRepositoryFetchURL();
    if (settingsUrl == null) return false;
    if (!url.getHost().equals(settingsUrl.getHost())) return false;
    if (url.getPort() != settingsUrl.getPort()) return false;
    if (!url.getPath().equals(settingsUrl.getPath())) return false;

    final String settingsBranch = getNullIfEmpty(mySettings.getBranch());
    if (branch != null && settingsBranch != null && !branch.equals(settingsBranch)) return false;

    return true;
  }

  @Nullable
  private static String getNullIfEmpty(@NotNull final String string) {
    final String trimmedString = string.trim();
    return trimmedString.length() > 0 ? trimmedString : null;
  }


  public static void invalidateRevisionsCache(VcsRoot root) {
    ourCache.invalidateCache(root);
  }


  /**
   * Revisions cache for whole server.
   * ThreadSafe.
   */
  private final static class RevisionsCache {
    //repositoryId -> per repository cache
    private final ConcurrentMap<String, RepositoryRevisionCache> myCache = new ConcurrentHashMap<String, RepositoryRevisionCache>();

    void invalidateCache(@NotNull final VcsRoot root) {
      myCache.remove(getRepositoryId(root));
    }

    RepositoryRevisionCache getRepositoryCache(@NotNull final VcsRoot root) {
      String repositoryId = getRepositoryId(root);
      RepositoryRevisionCache result = myCache.get(repositoryId);
      if (result == null) {
        result = new RepositoryRevisionCache();
        RepositoryRevisionCache old = myCache.putIfAbsent(repositoryId, result);
        result = (old == null) ? result : old;
      }
      return result;
    }

    private String getRepositoryId(@NotNull final VcsRoot root) {
      final StringBuilder builder = new StringBuilder();
      builder.append(root.getId());
      builder.append('_');
      builder.append(root.getPropertiesHash());
      return builder.toString();
    }
  }


  /**
   * Revisions cache for single repository.
   * ThreadSafe.
   */
  private final static class RepositoryRevisionCache {
    //revision (SHA) -> does this repository have such revision
    private final ConcurrentMap<String, Boolean> myCache = new ConcurrentHashMap<String, Boolean>();

    /**
     * @return true if repository has revision, false if doesn't, null if there is no data on this revision
     */
    @Nullable
    Boolean hasRevision(@NotNull String revision) {
      return myCache.get(revision);
    }

    void saveRevision(@NotNull String revision, boolean has) {
      myCache.put(revision, has);
    }
  }
}
