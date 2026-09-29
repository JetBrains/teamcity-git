package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.oauth.TokenRefresher;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.MergeOptions;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.mockito.Mockito;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * Verifies a rejected fetch or push URL ({@link GitRemoteUrlInspector.UrlRestriction}) is caught at every
 * {@link GitVcsSupport}/{@link GitCommitSupport}/{@link GitMergeSupport} entry point.
 */
public class RejectedRemoteUrlTest extends BaseRemoteRepositoryTest {
  private static final String VALID_URL = "https://example.com/repo.git";

  private GitVcsSupport myGit;

  @BeforeClass
  public void setUp() throws Exception {
    super.setUp();
    ServerPaths paths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    myGit = GitSupportBuilder.gitSupport().withServerPaths(paths).build();
    setInternalProperty(Constants.ALLOW_FILE_URL, "false");
  }

  @AfterClass
  public void tearDown() {
    super.tearDown();
  }

  @DataProvider(name = "rejectedUrls")
  public Object[][] rejectedUrls() {
    Object[][] base = {
      {"file:///tmp/repo.git", "is using local file %s URL 'file:///tmp/repo.git', which is forbidden for security reasons"},
      {"ext::sh /tmp/x.sh", "%s URL transport not allowed"},
      {"-oProxyCommand=id", "%s URL '-oProxyCommand=id' is malformed and cannot be used"}
    };

    List<Object[]> rows = new ArrayList<>();
    for (Object[] row : base) {
      String url = (String) row[0];
      String template = (String) row[1];
      rows.add(new Object[]{url, VALID_URL, String.format(template, "fetch")});
      rows.add(new Object[]{VALID_URL, url, String.format(template, "push")});
    }
    return rows.toArray(new Object[0][]);
  }

  private VcsRoot rootWith(String fetchUrl, String pushUrl) {
    return VcsRootBuilder.vcsRoot().withFetchUrl(fetchUrl).withPushUrl(pushUrl).build();
  }

  @Test(dataProvider = "rejectedUrls")
  public void creation_of_GitVcsRoots_throws(String fetchUrl, String pushUrl, String expectedMessageFragment) {
    try {
      new SGitVcsRoot(Mockito.mock(MirrorManager.class), rootWith(fetchUrl, pushUrl), Mockito.mock(URIishHelper.class), Mockito.mock(TokenRefresher.class));
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).contains(expectedMessageFragment);
    }
  }

  @Test(dataProvider = "rejectedUrls")
  public void testConnection_fails(String fetchUrl, String pushUrl, String expectedMessageFragment) {
    try {
      myGit.testConnection(rootWith(fetchUrl, pushUrl));
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).contains(expectedMessageFragment);
    }
  }

  @Test(dataProvider = "rejectedUrls")
  public void getCurrentState_fails(String fetchUrl, String pushUrl, String expectedMessageFragment) {
    try {
      myGit.getCurrentState(rootWith(fetchUrl, pushUrl));
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).contains(expectedMessageFragment);
    }
  }

  @Test(dataProvider = "rejectedUrls")
  public void labellingSupport_fails(String fetchUrl, String pushUrl, String expectedMessageFragment) {
    try {
      myGit.getLabelingSupport().label("123", "123", rootWith(fetchUrl, pushUrl), CheckoutRules.DEFAULT);
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).contains(expectedMessageFragment);
    }
  }

  @Test(dataProvider = "rejectedUrls")
  public void commitSupport_fails(String fetchUrl, String pushUrl, String expectedMessageFragment) {
    GitCommitSupport commitSupport = new GitCommitSupport(myGit, Mockito.mock(CommitLoader.class), Mockito.mock(RepositoryManager.class), Mockito.mock(GitRepoOperations.class));
    try {
      commitSupport.getCommitPatchBuilder(rootWith(fetchUrl, pushUrl));
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).contains(expectedMessageFragment);
    }
  }

  @Test(dataProvider = "rejectedUrls")
  public void mergeSupport_fails(String fetchUrl, String pushUrl, String expectedMessageFragment) {
    GitMergeSupport mergeSupport = new GitMergeSupport(myGit, Mockito.mock(CommitLoader.class), Mockito.mock(RepositoryManager.class), Mockito.mock(ServerPluginConfig.class), Mockito.mock(GitRepoOperations.class));
    try {
      mergeSupport.merge(rootWith(fetchUrl, pushUrl), "123", "123", "123", Mockito.mock(MergeOptions.class));
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).contains(expectedMessageFragment);
    }
  }
}
