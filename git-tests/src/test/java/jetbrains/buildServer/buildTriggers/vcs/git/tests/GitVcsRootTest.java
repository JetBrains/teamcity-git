

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.*;

/**
 * @author dmitry.neverov
 */
@Test
public class GitVcsRootTest extends BaseRemoteRepositoryTest {

  private MirrorManager myMirrorManager;
  private File myDefaultCachesDir;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myDefaultCachesDir = myTempFiles.createTempDir();
    ServerPaths serverPaths = new ServerPaths(myDefaultCachesDir.getAbsolutePath());
    ServerPluginConfig config = new PluginConfigBuilder(serverPaths).build();
    myMirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl(), new RemoteRepositoryUrlInvestigatorImpl());
  }

  public void fetch_url_for_repository_in_local_filesystem_should_not_contain_password() throws Exception {
    String pathInLocalFS = "/path/in/local/fs";
    VcsRoot root = vcsRoot().withFetchUrl(pathInLocalFS)
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername("user")
      .withPassword("pass")
      .build();
    GitVcsRoot s = new GitVcsRoot(myMirrorManager, root, new URIishHelperImpl());
    assertEquals(new URIish(pathInLocalFS), s.getRepositoryFetchURL().get());
  }

  public void cred_prod() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl("git://git@some.org/repository.git").build();
    GitVcsRoot gitRoot = new GitVcsRoot(myMirrorManager, root, new URIishHelperImpl());
    assertNull("User is not stripped from the url with anonymous protocol", gitRoot.getRepositoryFetchURL().getUser());
  }

  @TestFor(issues = "TW-36401")
  public void disabling_custom_clones() throws Exception {
    File cloneDir = new File("");
    VcsRoot root = vcsRoot().withRepositoryPathOnServer(cloneDir.getAbsolutePath()).withFetchUrl("http://some.org/repo").build();
    GitVcsRoot gitRoot1 = new GitVcsRoot(myMirrorManager, root, new URIishHelperImpl());
    assertTrue(FileUtil.isAncestor(myDefaultCachesDir, gitRoot1.getRepositoryDir(), true));
    setInternalProperty(Constants.CUSTOM_CLONE_PATH_ENABLED, "true");
    GitVcsRoot gitRoot2 = new GitVcsRoot(myMirrorManager, root, new URIishHelperImpl());
    assertEquals(cloneDir.getAbsoluteFile(), gitRoot2.getRepositoryDir());
  }

  @DataProvider(name = "urlsWithNewLines")
  public static Object[][] urlsWithNewLines() {
    return new Object[][] {
      new Object[] { "http://some.org/repo\n" },
      new Object[] { "http://some.org/repo\r" },
      new Object[] { "http://some.org/repo\n[section]" },
      new Object[] { "http://some.org/repo\r[section]" },
      new Object[] { "http://some.org/repo\r\n[section]" },
    };
  }

  @TestFor(issues = "TW-50043")
  @Test(dataProvider = "urlsWithNewLines")
  public void new_line_in_fetch_url(@NotNull String url) throws VcsException {
    try {
      new GitVcsRoot(myMirrorManager, vcsRoot().withFetchUrl(url).build(), new URIishHelperImpl());
      fail("No error for url '" + url + "'");
    } catch (VcsException e) {
      //expected
    }
  }

  @TestFor(issues = "TW-50043")
  @Test(dataProvider = "urlsWithNewLines")
  public void new_line_in_push_url(@NotNull String url) throws VcsException {
    try {
      new GitVcsRoot(myMirrorManager, vcsRoot().withFetchUrl("http://some.org/repo.git").withPushUrl(url).build(), new URIishHelperImpl());
      fail("No error for url '" + url + "'");
    } catch (VcsException e) {
      //expected
    }
  }
}