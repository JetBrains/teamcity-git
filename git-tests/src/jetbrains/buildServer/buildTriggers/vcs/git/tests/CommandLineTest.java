package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
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
      Thread lsRemoteThred = new Thread() {
        @Override
        public void run() {
          try {
            refs.set(lsRemote.call());
          } catch (VcsException e) {
            throw new RuntimeException(e);
          }
        }
      };
      lsRemoteThred.start();
      lsRemoteThred.join(10000);

      assertEquals(String.valueOf(i), 2, refs.get().size());
      assertTrue(refs.get().stream().map(r -> r.getName()).collect(Collectors.toList()).containsAll(Arrays.asList("HEAD", "refs/heads/master")));
    }
  }
}
