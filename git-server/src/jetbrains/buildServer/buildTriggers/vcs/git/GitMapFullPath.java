package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

/**
* @author kir
*/
class GitMapFullPath {

  private static final Logger LOG = Logger.getInstance(GitMapFullPath.class.getName());

  private static final Map<String, Boolean> ourHasRevisionsCache = createCacheMap(5);

  private final VcsRootEntry myRootEntry;
  private final String myFullPath;

  private final Settings mySettings;
  private final int myFirstSep;
  private final int myLastSep;

  private String myGitRevision;

  public GitMapFullPath(final VcsRootEntry rootEntry, final String fullPath, final Settings settings) {
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
    final Boolean hasRevision = ourHasRevisionsCache.get(revisionAndRootKey());
    if (hasRevision != null) return !hasRevision;

    Commit existingCommit = findCommit();
    ourHasRevisionsCache.put(revisionAndRootKey(), existingCommit != null);
    return existingCommit == null;
  }

  private Commit findCommit() throws VcsException, IOException {
    final Repository repository = GitVcsSupport.getRepository(mySettings, null);
    try {
      return repository.mapCommit(myGitRevision);
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
      branch = GitVcsSupport.getNullIfEmpty(repositoryUrlWithBranch.substring(branchSep + 1));
    }

    final URIish settingsUrl = mySettings.getRepositoryURL();
    if (settingsUrl == null) return false;
    if (!url.getHost().equals(settingsUrl.getHost())) return false;
    if (url.getPort() != settingsUrl.getPort()) return false;
    if (!url.getPath().equals(settingsUrl.getPath())) return false;

    final String settingsBranch = GitVcsSupport.getNullIfEmpty(mySettings.getBranch());
    if (branch != null && settingsBranch != null && !branch.equals(settingsBranch)) return false;

    return true;
  }

  private static Map<String, Boolean> createCacheMap(final int items) {
    return Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(items, 0.8f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, Boolean> eldest) {
          return size() > items;
        }
      });
  }

}
