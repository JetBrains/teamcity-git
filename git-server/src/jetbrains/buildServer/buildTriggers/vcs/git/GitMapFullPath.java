/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import jetbrains.buildServer.util.UptodateValue;
import jetbrains.buildServer.vcs.VcsException;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
* @author kir
*/
class GitMapFullPath {

  private static final Logger LOG = Logger.getInstance(GitMapFullPath.class.getName());

  private static final UptodateValue<Map<String, Boolean>> ourRevisionsCache = new UptodateValue<Map<String, Boolean>>(new UptodateValue.ValueProvider<Map<String, Boolean>>() {
      public Map<String, Boolean> getNewValue() {
        return new ConcurrentHashMap<String, Boolean>();
      }
    }, 10000);

  private final GitVcsSupport myGitSupport;
  private final VcsRootEntry myRootEntry;
  private final String myFullPath;

  private final Settings mySettings;
  private final int myFirstSep;
  private final int myLastSep;

  private String myGitRevision;

  public GitMapFullPath(final GitVcsSupport gitSupport, final VcsRootEntry rootEntry, final String fullPath, final Settings settings) {
    myGitSupport = gitSupport;
    myRootEntry = rootEntry;
    myFullPath = fullPath;

    myFirstSep = myFullPath.indexOf("|");
    myLastSep = myFullPath.lastIndexOf("|");
    mySettings = settings;
  }

  public Collection<String> mapFullPath() throws VcsException {
    if (invalidFormat())
      return Collections.emptySet();

    try {
      initRevision();

      if (hasRevision()) {
        if (noSuchRevisionInRepository()) return Collections.emptySet();
      } else {
        if (!matchRepositoryByUrl(repositoryUrlWithBranch())) return Collections.emptySet();
      }

    } catch (final IOException e) {
      throw new VcsException(e);
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

  private boolean noSuchRevisionInRepository() throws IOException, VcsException {
    final Boolean hasRevision = ourRevisionsCache.getValue().get(revisionAndRootKey());
    if (hasRevision != null) return !hasRevision;

    RevCommit existingCommit = null;
    try {
      existingCommit = findCommit();
    } catch (IOException e) {
      //commit not found, ignore exception
    }
    ourRevisionsCache.getValue().put(revisionAndRootKey(), existingCommit != null);
    return existingCommit == null;
  }

  private RevCommit findCommit() throws VcsException, IOException {
    final Repository repository = myGitSupport.getRepository(mySettings);
    try {
      return myGitSupport.getCommit(repository, myGitRevision);
    } finally {
      repository.close();
    }
  }

  private String revisionAndRootKey() {
    return myGitRevision + "_" + myRootEntry.getSignature();
  }

  private String repositoryUrlWithBranch() {
    return myFullPath.substring(myFirstSep + 1, myLastSep).trim();
  }

  private boolean hasRevision() {
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
}
