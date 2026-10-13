package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.intellij.openapi.util.Trinity;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.impl.ssh.AgentSshKnownHostsManagerImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitFacade;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitFacadeImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentPluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.BuildContext;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.URIishHelperImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.Branches;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.DeleteBranchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.UpdateRefBatchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.DeleteBranchCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.UpdateRefBatchCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.AddCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.ContextImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitFacade;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.AddCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitFacadeImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.LsRemoteCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.StubContext;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.CommandLineUtil.GIT_CLI_LONG_MESSAGES_SEPARATOR;
import static jetbrains.buildServer.buildTriggers.vcs.git.CommandLineUtil.cropOutputMessage;
import static jetbrains.buildServer.buildTriggers.vcs.git.Constants.GIT_STDERR_FAILURE_SUBSTRINGS_PARAM;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder.runningBuild;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.testng.AssertJUnit.*;

@Test(dataProviderClass = GitVersionProvider.class, dataProvider = "version")
public class CommandLineTest extends BaseRemoteRepositoryTest {
  private static final String COMMIT_GRAPH_TEST_REPO = "TW-100479/remote/commit_graph_test_repo";
  private static final String COMMIT_GRAPH_TEST_REPO_MAIN_REVISION = "64c8558fbd0beb9f5639f66082e6a1d092e30a93";
  private static final String AMBIGUOUS_REFNAME_FAILURE_SUBSTRINGS = "unrelated substring;Is Ambiguous";

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
    setInternalProperty("teamcity.git.extra.credentials.enable", "true");
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

      @Override
      public boolean isCleanCredHelperScript() {
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

  @TestFor(issues = {"TW-99549", "TW-99557"})
  public void test_crop_too_big_output(@NotNull GitExec git) throws Throwable {
    setInternalProperty("teamcity.git.error.message.maxLength", "100");
    createSources(git);

    File outputFile = FileUtil.createTempFile("proc", "output");
    FileUtil.writeFile(outputFile, "failed command " + String.join("", Collections.nCopies(100, "error1 error2 error3 error4 error5\n")));



    GitCommandLine cmd = createRepositoryCmd(git);

    cmd.addParameter("failed command " + String.join("", Collections.nCopies(100, "error1 error2 error3 error4 error5\n")));

    VcsException result = null;
    try {
      CommandUtil.runCommand(cmd);
      fail();
    } catch (VcsException e) {
      result = e;
    }

    assertNotNull(result);
    assertTrue(result.getMessage().contains("exit code: 1"));

    String error = result.getMessage().substring(result.getMessage().indexOf("stderr: ") + "stderr: ".length());

    assertTrue(error.length() <= 100 && error.length() >= 98);

    assertTrue(error.contains("error1 error2 error3 error4 error5"));
    assertTrue(error.contains("continue>"));
  }

  @TestFor(issues = {"TW-99549", "TW-99557"})
  public void test_no_crop_too_small_output(@NotNull GitExec git) throws Throwable {
    setInternalProperty("teamcity.git.error.message.maxLength", "100");
    createSources(git);
    GitCommandLine cmd = createRepositoryCmd(git);


    cmd.addParameter("fail");

    VcsException result = null;
    try {
      CommandUtil.runCommand(cmd);
      fail();
    } catch (VcsException e) {
      result = e;
    }

    assertNotNull(result);
    assertTrue(result.getMessage().contains("exit code: 1"));

    String error = result.getMessage().substring(result.getMessage().indexOf("stderr: ") + "stderr: ".length());

    assertTrue(error.length() <= 100);

    assertFalse(error.contains("continue>"));
  }

  @TestFor(issues = "TW-100871")
  public void add_embedded_repository_fails(@NotNull GitExec git) throws Exception {
    File repo = copyWorkingTreeRepository(COMMIT_GRAPH_TEST_REPO, new File(myTempFiles.createTempDir(), "repo"));
    copyWorkingTreeRepository(COMMIT_GRAPH_TEST_REPO, new File(repo, "embedded"));

    AddCommand add = new AddCommandImpl(createCmd(git, repo)).setPaths(Collections.singletonList("embedded"));

    // git exits with 0 here, so the failure comes from the warning on stderr
    thenThrownBy(add::call)
      .isInstanceOf(VcsException.class)
      .hasMessageContaining("adding embedded git repository: embedded")
      .hasMessageNotContaining("exit code:");
  }

  @TestFor(issues = "TW-100871")
  public void add_all_with_unreadable_directory_fails(@NotNull GitExec git) throws Exception {
    File repo = copyWorkingTreeRepository(COMMIT_GRAPH_TEST_REPO, new File(myTempFiles.createTempDir(), "repo"));
    File unreadable = new File(repo, "unreadable");
    assertTrue(unreadable.mkdir());
    FileUtil.writeFileAndReportErrors(new File(unreadable, "untracked.txt"), "untracked");
    if (!unreadable.setReadable(false) || unreadable.canRead()) {
      throw new SkipException("Cannot make a directory unreadable on this platform or user");
    }

    try {
      AddCommand add = new AddCommandImpl(createCmd(git, repo)).setAddAll(true);

      // git exits with 0 and leaves the directory out of the index
      thenThrownBy(add::call)
        .isInstanceOf(VcsException.class)
        .hasMessageContaining("could not open directory 'unreadable/'")
        .hasMessageNotContaining("exit code:");
    } finally {
      unreadable.setReadable(true);
    }
  }

  @TestFor(issues = "TW-100871")
  public void delete_branch_with_locked_config_fails(@NotNull GitExec git) throws Exception {
    File repo = copyWorkingTreeRepository(COMMIT_GRAPH_TEST_REPO, new File(myTempFiles.createTempDir(), "repo"));
    AgentGitFacadeImpl facade = new AgentGitFacadeImpl(git.getPath(), repo);
    facade.setConfig().setPropertyName("branch.feature-a.remote").setValue("origin").call();
    File configLock = new File(repo, ".git/config.lock");
    assertTrue(configLock.createNewFile());

    DeleteBranchCommand deleteBranch = new DeleteBranchCommandImpl(createCmd(git, repo)).setName("feature-a");

    thenThrownBy(deleteBranch::call)
      .isInstanceOf(VcsException.class)
      .hasMessageContaining("update of config-file failed")
      .hasMessageNotContaining("exit code:");

    // git deleted the branch but could not remove its config section
    FileUtil.delete(configLock);
    then(facade.showRef().call().getValidRefs()).containsKey("refs/heads/main").doesNotContainKey("refs/heads/feature-a");
    then(FileUtil.readText(new File(repo, ".git/config"))).contains("[branch \"feature-a\"]");
  }

  @TestFor(issues = "TW-100871")
  public void update_ref_batch_with_empty_new_value_fails(@NotNull GitExec git) throws Exception {
    File repo = copyWorkingTreeRepository(COMMIT_GRAPH_TEST_REPO, new File(myTempFiles.createTempDir(), "repo"));
    UpdateRefBatchCommand updateRef = new UpdateRefBatchCommandImpl(createCmd(git, repo)).update("refs/heads/feature-b", "", null);

    thenThrownBy(updateRef::call)
      .isInstanceOf(VcsException.class)
      .hasMessageContaining("update refs/heads/feature-b: missing <new-oid>, treating as zero")
      .hasMessageNotContaining("exit code:");

    // git treated the empty value as a zero id and deleted the branch
    then(new AgentGitFacadeImpl(git.getPath(), repo).showRef().call().getValidRefs()).containsKey("refs/heads/main").doesNotContainKey("refs/heads/feature-b");
  }

  /**
   * TW-100871/broken_ref_repo:
   * <pre>
   * * bc92f5f add feature      &lt;- feature
   * | * 9bb22ce change on main  &lt;- main, HEAD
   * |/
   * * bd5108a initial commit
   * refs/heads/broken = "garbage"
   * </pre>
   * The "ignoring broken ref" warning is tolerated by default and fails the command once added to the internal property.
   */
  @TestFor(issues = "TW-100871")
  public void list_branches_with_broken_ref_fails_only_with_custom_substring(@NotNull GitExec git) throws Exception {
    File repo = copyWorkingTreeRepository("TW-100871/broken_ref_repo", new File(myTempFiles.createTempDir(), "repo"));
    AgentGitFacadeImpl facade = new AgentGitFacadeImpl(git.getPath(), repo);

    // git warns about the broken ref and exits with 0, the warning is not in the default list
    Branches branches = facade.listBranches(false);
    then(branches.isCurrentBranch("main")).isTrue();
    then(branches.contains("feature")).isTrue();
    then(branches.contains("broken")).isFalse();

    // every command reads the property when its command line is created
    setInternalProperty(GIT_STDERR_FAILURE_SUBSTRINGS_PARAM, "unrelated substring; Ignoring Broken Ref ");

    thenThrownBy(() -> facade.listBranches(false))
      .isInstanceOf(VcsException.class)
      .hasMessageContaining("ignoring broken ref refs/heads/broken")
      .hasMessageNotContaining("exit code:");
  }

  /**
   * The tag refs/tags/feature-a points to main, so the short name feature-a is ambiguous and git resolves it to the tag.
   * The warning is tolerated by default and fails the command once added to the agent build parameter.
   */
  @TestFor(issues = "TW-100871")
  public void update_ref_with_ambiguous_name_fails_only_with_agent_build_parameter(@NotNull GitExec git) throws Exception {
    File repo = createRepositoryWithAmbiguousRefName(git);
    checkAmbiguousUpdateRefTakesTag(git, repo, createAgentFacade(git, repo));
    checkAmbiguousUpdateRefFails(createAgentFacade(git, repo, PluginConfigImpl.GIT_STDERR_FAILURE_SUBSTRINGS_PARAM_AGENT, AMBIGUOUS_REFNAME_FAILURE_SUBSTRINGS));
  }

  /**
   * The same scenario as {@link #update_ref_with_ambiguous_name_fails_only_with_agent_build_parameter}, with the list read by the server plugin config.
   */
  @TestFor(issues = "TW-100871")
  public void update_ref_with_ambiguous_name_fails_only_with_server_internal_property(@NotNull GitExec git) throws Exception {
    File repo = createRepositoryWithAmbiguousRefName(git);
    checkAmbiguousUpdateRefTakesTag(git, repo, createServerFacade(git, repo));

    // the server config reads the property when each command line is created
    setInternalProperty(GIT_STDERR_FAILURE_SUBSTRINGS_PARAM, AMBIGUOUS_REFNAME_FAILURE_SUBSTRINGS);
    checkAmbiguousUpdateRefFails(createServerFacade(git, repo));
  }

  @NotNull
  private File createRepositoryWithAmbiguousRefName(@NotNull GitExec git) throws Exception {
    File repo = copyWorkingTreeRepository(COMMIT_GRAPH_TEST_REPO, new File(myTempFiles.createTempDir(), "repo"));
    new AgentGitFacadeImpl(git.getPath(), repo).updateRef().setRef("refs/tags/feature-a").setRevision(COMMIT_GRAPH_TEST_REPO_MAIN_REVISION).call();
    return repo;
  }

  private static void checkAmbiguousUpdateRefTakesTag(@NotNull GitExec git, @NotNull File repo, @NotNull GitFacade facade) throws VcsException {
    // git warns about the ambiguous name, exits with 0 and takes the tag instead of the branch
    facade.updateRef().setRef("refs/heads/copy").setRevision("feature-a").call();
    Ref copy = new AgentGitFacadeImpl(git.getPath(), repo).showRef().call().getValidRefs().get("refs/heads/copy");
    then(copy.getObjectId().name()).isEqualTo(COMMIT_GRAPH_TEST_REPO_MAIN_REVISION);
  }

  private static void checkAmbiguousUpdateRefFails(@NotNull GitFacade facade) {
    thenThrownBy(() -> facade.updateRef().setRef("refs/heads/strict-copy").setRevision("feature-a").call())
      .isInstanceOf(VcsException.class)
      .hasMessageContaining("refname 'feature-a' is ambiguous")
      .hasMessageNotContaining("exit code:");
  }

  @NotNull
  private GitFacade createServerFacade(@NotNull GitExec git, @NotNull File repo) throws IOException {
    ServerPluginConfig config = new jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath()));
    return new GitFacadeImpl(repo, new ContextImpl(null, config, git, myKnownHostsManager));
  }

  @NotNull
  private AgentGitFacade createAgentFacade(@NotNull GitExec git, @NotNull File repo, @NotNull String... sharedConfigParams) throws Exception {
    VcsRootImpl root = vcsRoot().withFetchUrl(repo).build();
    AgentRunningBuild build = runningBuild()
      .sharedEnvVariable(Constants.TEAMCITY_AGENT_GIT_PATH, git.getPath())
      .sharedConfigParams(sharedConfigParams)
      .withAgentConfiguration(myAgentConfiguration)
      .addRoot(root)
      .build();
    AgentPluginConfig config = myBuilder.getPluginConfigFactory().createConfig(build, root);
    BuildContext context = new BuildContext(build, config, new AgentSshKnownHostsManagerImpl());
    return myBuilder.getGitMetaFactory().createFactory(myBuilder.getGitAgentSSHService(), context).create(repo);
  }

  @NotNull
  private static File copyWorkingTreeRepository(@NotNull String dataPath, @NotNull File destDir) throws IOException {
    FileUtil.copyDir(dataFile(dataPath), destDir);
    FileUtil.rename(new File(destDir, "_git1"), new File(destDir, ".git"));
    return destDir;
  }

  @NotNull
  private static GitCommandLine createCmd(@NotNull GitExec git, @NotNull File repo) {
    GitCommandLine cmd = new GitCommandLine(new StubContext(git.getPath(), git.getVersion()), new AgentGitFacadeImpl(git.getPath()).getScriptGen());
    cmd.setExePath(git.getPath());
    cmd.setWorkingDirectory(repo);
    return cmd;
  }

  public void test_message_crop(@NotNull GitExec git) {
    String len100 = "This string is exactly one hundred characters long (100 chars) as requested for this specific task!";
    String cropped1 = cropOutputMessage(len100, 50);
    assertTrue(cropped1.length() <= 50 && cropped1.length() >= 48);
    assertTrue(cropped1.contains("This string is exactly"));
    assertTrue(cropped1.contains("!"));

    String len50 = "This string is exactly 50 characters in length!!!!";
    String cropped2 = cropOutputMessage(len50, 39);
    assertEquals(cropped2.substring(0, 39), cropped2);

    assertEquals(len100, cropOutputMessage(len100, 150));

    assertEquals(len100, cropOutputMessage(len100, -1));

    assertEquals("", cropOutputMessage(len100, 0));

    assertEquals("Thi", cropOutputMessage(len100, 3));
    assertEquals("Th", cropOutputMessage(len100, 2));

    int separatorLength = GIT_CLI_LONG_MESSAGES_SEPARATOR.length(); // 15
    assertEquals("This string is ", cropOutputMessage(len100, separatorLength));
    assertEquals("This string is", cropOutputMessage(len100, separatorLength-1));
    assertEquals("This string i", cropOutputMessage(len100, separatorLength-2));
    assertEquals("This string ", cropOutputMessage(len100, separatorLength-3));
    assertEquals("This string is e", cropOutputMessage(len100, separatorLength+1));
    assertEquals("This string is ex", cropOutputMessage(len100, separatorLength+2));
    assertEquals("This string is exa", cropOutputMessage(len100, separatorLength+3));


    for (int i = 0; i <= 100; i++) {
      assertTrue(i - cropOutputMessage(len100, i).length() >= 0 && i - cropOutputMessage(len100, i).length() <= 1);
    }
  }

}
