

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.ssh.SshKnownHostsManager;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.getVcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author dmitry.neverov
 */
@Test
public class TransportFactoryTest {

  private TempFiles myTempFiles;
  private PluginConfigBuilder myConfigBuilder;
  private ServerPaths myServerPaths;
  private SshKnownHostsManager mySshKnownHostsManager;

  @BeforeMethod
  public void setUp() throws Exception {
    myTempFiles = new TempFiles();
    File dotBuildServer = myTempFiles.createTempDir();
    myServerPaths = new ServerPaths(dotBuildServer.getAbsolutePath());
    myConfigBuilder = new PluginConfigBuilder(myServerPaths);
  }


  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }


  public void transport_should_have_timeout_specified_in_config() throws Exception {
    myConfigBuilder.setIdleTimeoutSeconds(20);
    ServerPluginConfig config = myConfigBuilder.build();
    VcsRootSshKeyManager manager = new EmptyVcsRootSshKeyManager();
    TransportFactory transportFactory = new TransportFactoryImpl(config, manager, mySshKnownHostsManager);
    Transport transport = createTransport(transportFactory);
    assertEquals(20, transport.getTimeout());
  }


  @TestFor(issues = "TW-25087")
  public void transport_for_anonymous_protocol_should_not_have_credentials() throws Exception {
    File tmp = myTempFiles.createTempDir();
    Repository r = new RepositoryBuilder().setBare().setGitDir(tmp).build();
    VcsRoot root = vcsRoot().withFetchUrl("git://some.org/repo.git")
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername("user")
      .withPassword("pwd")
      .build();
    AuthSettings authSettings = new AuthSettingsImpl(root, new URIishHelperImpl());
    TransportFactory factory = new TransportFactoryImpl(myConfigBuilder.build(), new EmptyVcsRootSshKeyManager(), mySshKnownHostsManager);
    factory.createTransport(r, new URIishHelperImpl().createAuthURI(authSettings, "git://some.org/repo.git").get(), authSettings);
  }


  private Transport createTransport(TransportFactory factory) throws Exception {
    File original = dataFile("repo.git");
    File copy = myTempFiles.createTempDir();
    FileUtil.copyDir(original, copy);

    VcsRootImpl root = getVcsRoot(copy);
    MirrorManager mirrorManager = new MirrorManagerImpl(myConfigBuilder.build(), new HashCalculatorImpl(), new RemoteRepositoryUrlInvestigatorImpl());
    GitVcsRoot gitRoot = new GitVcsRoot(mirrorManager, root, new URIishHelperImpl());
    Repository repository = new RepositoryBuilder().setGitDir(copy).setBare().build();
    return factory.createTransport(repository, new URIish(GitUtils.toURL(original)), gitRoot.getAuthSettings());
  }
}