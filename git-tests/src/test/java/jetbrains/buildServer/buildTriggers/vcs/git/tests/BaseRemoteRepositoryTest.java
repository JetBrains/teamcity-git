

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.TestInternalProperties;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.BuildAgentConfigurationBuilder;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.util.InternalPropertiesHandler;
import jetbrains.buildServer.log.LogInitializer;
import jetbrains.buildServer.serverSide.impl.ssh.ServerSshKnownHostsManagerImpl;
import jetbrains.buildServer.ssh.SshKnownHostsManager;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;

public abstract class BaseRemoteRepositoryTest {

  protected TempFiles myTempFiles;
  protected BuildAgentConfiguration myAgentConfiguration;
  private String[] myRepositories;
  private Map<String, File> myRemoteRepositories;
  protected SshKnownHostsManager myKnownHostsManager = new ServerSshKnownHostsManagerImpl();
  private InternalPropertiesHandler myInternalPropertiesHandler;

  protected BaseRemoteRepositoryTest(String... repositories) {
    myRepositories = repositories;
  }

  @BeforeClass
  public void setUpClass() {
    LogInitializer.setUnitTest(true);
  }

  @BeforeMethod
  public void setUp() throws Exception {
    TestInternalProperties.init();
    myTempFiles = new TempFiles();
    File tmp = myTempFiles.createTempDir();
    myRemoteRepositories = new HashMap<String, File>();
    for (String r : myRepositories) {
      File remoteRepository = new File(tmp, r);
      copyRepository(dataFile(r), remoteRepository);
      myRemoteRepositories.put(r, remoteRepository);
    }
    myAgentConfiguration = BuildAgentConfigurationBuilder.agentConfiguration(myTempFiles.createTempDir(), myTempFiles.createTempDir(), myTempFiles.createTempDir())
      .build();
    myInternalPropertiesHandler = new InternalPropertiesHandler();
  }

  @AfterMethod
  public void tearDown() {
    cleanInternalProperties();
    myTempFiles.cleanup();
  }

  protected String getRemoteRepositoryUrl(@NotNull String remoteRepository) {
    File remote = myRemoteRepositories.get(remoteRepository);
    if (remote == null)
      throw new IllegalArgumentException("No remote repository found: " + remoteRepository);
    return GitUtils.toURL(remote);
  }

  protected File getRemoteRepositoryDir(@NotNull String remoteRepository) {
    return myRemoteRepositories.get(remoteRepository);
  }

  @DataProvider(name = "true,false")
  public static Object[][] createData() {
    return new Object[][] {
      new Object[] { Boolean.TRUE },
      new Object[] { Boolean.FALSE }
    };
  }

  protected void setInternalProperty(@NotNull String propKey, @NotNull String value) {
    myInternalPropertiesHandler.setInternalProperty(propKey, value);
  }

  private void cleanInternalProperties() {
    myInternalPropertiesHandler.tearDown();
  }
}