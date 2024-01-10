package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.intellij.openapi.util.Trinity;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitFacadeImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.URIishHelperImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.LsRemoteCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.StubContext;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder.runningBuild;
import static org.testng.AssertJUnit.*;

@Test(dataProviderClass = GitVersionProvider.class, dataProvider = "version")
public class CommandLineTest extends BaseRemoteRepositoryTest {
  private AgentSupportBuilder myBuilder;
  private GitAgentVcsSupport myVcsSupport;
  private GitHttpServer myServer;
  private VcsRootImpl myRoot;
  private File myBuildDir;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myBuilder = new AgentSupportBuilder(myTempFiles);
    myVcsSupport = myBuilder.build();
    myRoot = null;
    myBuildDir = null;
  }

  @Override
  @AfterMethod
  public void tearDown() {
    super.tearDown();
    if (myServer != null)
      myServer.stop();
  }

  private void createSources(@NotNull GitExec git) throws Exception {
    File repo = copyRepository(myTempFiles, dataFile("repo_for_fetch.1"), "repo.git");

    Random r = new Random();
    final String user = "user";
    final String password = String.valueOf(r.nextInt(100));

    myServer = new GitHttpServer(git.getPath(), repo);
    myServer.setCredentials(user, password);
    myServer.start();

    myRoot = vcsRoot()
      .withFetchUrl(myServer.getRepoUrl())
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername(user)
      .withPassword(password)
      .withBranch("master")
      .build();

    myBuildDir = myTempFiles.createTempDir();
    AgentRunningBuild build = runningBuild()
      .sharedEnvVariable(Constants.TEAMCITY_AGENT_GIT_PATH, git.getPath())
      .sharedConfigParams(PluginConfigImpl.USE_ALTERNATES, "true")
      .withAgentConfiguration(myAgentConfiguration)
      .withCheckoutDir(myBuildDir)
      .addRoot(myRoot)
      .build();

    //run first build to initialize mirror:
    CheckoutSources checkout = new CheckoutSources(myRoot, "add81050184d3c818560bdd8839f50024c188586", myBuildDir, build, myVcsSupport);
    checkout.run(TimeUnit.SECONDS.toMillis(10));
    assertTrue(checkout.success());
  }

  private GitCommandLine createRepositoryCmd(@NotNull GitExec git) throws Exception {
    final GitVersion version = new AgentGitFacadeImpl(git.getPath()).version().call();
    if (!GitVersion.fetchSupportsStdin(version)) throw new SkipException("Git version is too old to run this test");
    final GitCommandLine cmd = new GitCommandLine(new StubContext(git.getPath(), version) {
      @Override
      public boolean isProvideCredHelper() {
        return true;
      }
    }, new AgentGitFacadeImpl(git.getPath()).getScriptGen());

    cmd.setExePath(git.getPath());
    cmd.setWorkingDirectory(myBuildDir);

    return cmd;
  }

  public void git_locale_env_test(@NotNull GitExec git) throws Exception {
    createSources(git);
    GitCommandLine cmd = createRepositoryCmd(git);

    LsRemoteCommandImpl lsRemote = new LsRemoteCommandImpl(cmd);
    GitVcsRoot gitRoot = new GitVcsRoot(myBuilder.getMirrorManager(), myRoot, new URIishHelperImpl());
    lsRemote.setAuthSettings(gitRoot.getAuthSettings());

    lsRemote.call();

    assertNotNull(cmd.getEnvParams());
    assertEquals("en_US", cmd.getEnvParams().get("LANGUAGE"));
  }

  public void git_ls_remote_command_result_test(@NotNull GitExec git) throws Exception {
    createSources(git);
    for(int i = 0; i < 5; ++i) {
      GitCommandLine cmd = createRepositoryCmd(git);
      LsRemoteCommandImpl lsRemote = new LsRemoteCommandImpl(cmd);
      GitVcsRoot gitRoot = new GitVcsRoot(myBuilder.getMirrorManager(), myRoot, new URIishHelperImpl());
      lsRemote.setAuthSettings(gitRoot.getAuthSettings());

      AtomicReference<List<Ref>> refs = new AtomicReference<>();
      Thread lsRemoteThread = new Thread() {
        @Override
        public void run() {
          try {
            refs.set(lsRemote.call());
          } catch (VcsException e) {
            throw new RuntimeException(e);
          }
        }
      };
      lsRemoteThread.start();
      lsRemoteThread.join(10000);

      assertEquals(String.valueOf(i), 2, refs.get().size());
      assertTrue(refs.get().stream().map(r -> r.getName()).collect(Collectors.toList()).containsAll(Arrays.asList("HEAD", "refs/heads/master")));
    }
  }

  public void git_default_lfs_creds(@NotNull GitExec git) throws Exception {
    createSources(git);
    GitCommandLine cmd = createRepositoryCmd(git);
    LsRemoteCommandImpl lsRemote = new LsRemoteCommandImpl(cmd);
    GitVcsRoot gitRoot = new GitVcsRoot(myBuilder.getMirrorManager(), myRoot, new URIishHelperImpl());
    lsRemote.setAuthSettings(gitRoot.getAuthSettings());

    AtomicReference<List<Ref>> refs = new AtomicReference<>();
    Thread lsRemoteThread = new Thread() {
      @Override
      public void run() {
        try {
          refs.set(lsRemote.call());
        } catch (VcsException e) {
          throw new RuntimeException(e);
        }
      }
    };
    lsRemoteThread.start();
    lsRemoteThread.join(10000);

    Map<String, String> envParams = cmd.getEnvParams();
    assertNotNull(envParams);
    assertEquals("true", envParams.get("TEAMCITY_GIT_CREDENTIALS_MATCH_ALL_URLS"));
    assertEquals("user", envParams.get("TEAMCITY_GIT_CREDENTIALS_1_USER"));
    assertEquals(myServer.getPassword(), envParams.get("TEAMCITY_GIT_CREDENTIALS_1_PWD"));
    assertEquals(myServer.getRepoUrl() + "/info/lfs", envParams.get("TEAMCITY_GIT_CREDENTIALS_1_URL"));
    assertFalse(envParams.containsKey("TEAMCITY_GIT_CREDENTIALS_2_URL"));
  }

  public void git_additional_lfs_creds(@NotNull GitExec git) throws Exception {
    createSources(git);
    GitCommandLine cmd = createRepositoryCmd(git);
    LsRemoteCommandImpl lsRemote = new LsRemoteCommandImpl(cmd);
    List<ExtraHTTPCredentials> extraHTTPCredentials = new ArrayList<ExtraHTTPCredentials>() {{
      add(new ExtraHTTPCredentialsImpl("https://aaaaaa.bbbbb/path/to/lfs/info", "admin", "pass12345"));
    }};
    GitVcsRoot gitRoot = new GitVcsRoot(myBuilder.getMirrorManager(), myRoot, new URIishHelperImpl(), extraHTTPCredentials);
    lsRemote.setAuthSettings(gitRoot.getAuthSettings());

    AtomicReference<List<Ref>> refs = new AtomicReference<>();
    Thread lsRemoteThread = new Thread() {
      @Override
      public void run() {
        try {
          refs.set(lsRemote.call());
        } catch (VcsException e) {
          throw new RuntimeException(e);
        }
      }
    };
    lsRemoteThread.start();
    lsRemoteThread.join(10000);

    Map<String, String> envParams = cmd.getEnvParams();
    assertNotNull(envParams);
    assertEquals("false", envParams.getOrDefault("TEAMCITY_GIT_CREDENTIALS_MATCH_ALL_URLS", "false"));
    assertTrue(envParams.containsKey("TEAMCITY_GIT_CREDENTIALS_1_USER"));
    assertTrue(envParams.containsKey("TEAMCITY_GIT_CREDENTIALS_2_USER"));
    Trinity<String, String, String> defaultCreds;
    Trinity<String, String, String> additionalCreds;

    if (envParams.get("TEAMCITY_GIT_CREDENTIALS_1_USER").equals("user")) {
      defaultCreds = Trinity.create(envParams.get("TEAMCITY_GIT_CREDENTIALS_1_URL"),
                                    envParams.get("TEAMCITY_GIT_CREDENTIALS_1_USER"),
                                    envParams.get("TEAMCITY_GIT_CREDENTIALS_1_PWD"));
      additionalCreds = Trinity.create(envParams.get("TEAMCITY_GIT_CREDENTIALS_2_URL"),
                                       envParams.get("TEAMCITY_GIT_CREDENTIALS_2_USER"),
                                       envParams.get("TEAMCITY_GIT_CREDENTIALS_2_PWD"));

    } else if (envParams.get("TEAMCITY_GIT_CREDENTIALS_1_USER").equals("admin")) {
      defaultCreds = Trinity.create(envParams.get("TEAMCITY_GIT_CREDENTIALS_2_URL"),
                                    envParams.get("TEAMCITY_GIT_CREDENTIALS_2_USER"),
                                    envParams.get("TEAMCITY_GIT_CREDENTIALS_2_PWD"));
      additionalCreds = Trinity.create(envParams.get("TEAMCITY_GIT_CREDENTIALS_1_URL"),
                                       envParams.get("TEAMCITY_GIT_CREDENTIALS_1_USER"),
                                       envParams.get("TEAMCITY_GIT_CREDENTIALS_1_PWD"));
    }
    else {
      fail();
      return;
    }

    assertEquals(myServer.getRepoUrl() + "/info/lfs", defaultCreds.first);
    assertEquals("user", defaultCreds.second);
    assertEquals(myServer.getPassword(), defaultCreds.third);

    assertEquals("https://aaaaaa.bbbbb/path/to/lfs/info", additionalCreds.first);
    assertEquals("admin", additionalCreds.second);
    assertEquals("pass12345", additionalCreds.third);

    assertFalse(envParams.containsKey("TEAMCITY_GIT_CREDENTIALS_3_URL"));
  }

  public void git_additional_lfs_and_submodule_creds(@NotNull GitExec git) throws Exception {
    createSources(git);
    GitCommandLine cmd = createRepositoryCmd(git);
    LsRemoteCommandImpl lsRemote = new LsRemoteCommandImpl(cmd);
    List<ExtraHTTPCredentials> extraHTTPCredentials = new ArrayList<ExtraHTTPCredentials>() {{
      add(new ExtraHTTPCredentialsImpl("https://aaaaaa.bbbbb/path/to/lfs/info", "admin", "pass12345"));
      add(new ExtraHTTPCredentialsImpl("https://ccccc.dddd/path/to/submodule.git", "submodule_admin", "pass54321"));
      add(new ExtraHTTPCredentialsImpl("https://ccccc.dddd/path/to/submodule2222222.git", "submodule_admin1", "12pass345"));
    }};

    GitVcsRoot gitRoot = new GitVcsRoot(myBuilder.getMirrorManager(), myRoot, new URIishHelperImpl(), extraHTTPCredentials);
    lsRemote.setAuthSettings(gitRoot.getAuthSettings());

    AtomicReference<List<Ref>> refs = new AtomicReference<>();
    Thread lsRemoteThread = new Thread() {
      @Override
      public void run() {
        try {
          refs.set(lsRemote.call());
        } catch (VcsException e) {
          throw new RuntimeException(e);
        }
      }
    };
    lsRemoteThread.start();
    lsRemoteThread.join(10000);

    Map<String, String> envParams = cmd.getEnvParams();
    assertNotNull(envParams);
    assertEquals("false", envParams.getOrDefault("TEAMCITY_GIT_CREDENTIALS_MATCH_ALL_URLS", "false"));

    Map<String, Map<String, String>> envCreds = new HashMap<>();
    int i = 1;
    for (; i < 5; ++i) {
      assertTrue(envParams.containsKey("TEAMCITY_GIT_CREDENTIALS_" + i + "_URL"));
      Map<String, String> userPwd = new HashMap<>();
      userPwd.put("user", envParams.get("TEAMCITY_GIT_CREDENTIALS_" + i + "_USER"));
      userPwd.put("pwd", envParams.get("TEAMCITY_GIT_CREDENTIALS_" + i + "_PWD"));
      envCreds.put(envParams.get("TEAMCITY_GIT_CREDENTIALS_" + i + "_URL"), userPwd);
    }
    assertFalse(envParams.containsKey("TEAMCITY_GIT_CREDENTIALS_" + i + "_URL"));

    assertEquals(4, envCreds.size());

    assertTrue(envCreds.containsKey(myServer.getRepoUrl() + "/info/lfs"));
    assertEquals("user", envCreds.get(myServer.getRepoUrl() + "/info/lfs").get("user"));
    assertEquals(myServer.getPassword(), envCreds.get(myServer.getRepoUrl() + "/info/lfs").get("pwd"));

    assertTrue(envCreds.containsKey("https://aaaaaa.bbbbb/path/to/lfs/info"));
    assertEquals("admin", envCreds.get("https://aaaaaa.bbbbb/path/to/lfs/info").get("user"));
    assertEquals("pass12345", envCreds.get("https://aaaaaa.bbbbb/path/to/lfs/info").get("pwd"));

    assertTrue(envCreds.containsKey("https://ccccc.dddd/path/to/submodule.git"));
    assertEquals("submodule_admin", envCreds.get("https://ccccc.dddd/path/to/submodule.git").get("user"));
    assertEquals("pass54321", envCreds.get("https://ccccc.dddd/path/to/submodule.git").get("pwd"));

    assertTrue(envCreds.containsKey("https://ccccc.dddd/path/to/submodule2222222.git"));
    assertEquals("submodule_admin1", envCreds.get("https://ccccc.dddd/path/to/submodule2222222.git").get("user"));
    assertEquals("12pass345", envCreds.get("https://ccccc.dddd/path/to/submodule2222222.git").get("pwd"));
  }

}
