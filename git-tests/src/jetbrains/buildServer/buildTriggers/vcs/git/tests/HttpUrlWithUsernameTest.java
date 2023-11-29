/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManagerImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.URIishHelperImpl;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder.runningBuild;
import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("ALL")
@Test
@TestFor(issues = "TW-48103")
public class HttpUrlWithUsernameTest extends BaseRemoteRepositoryTest {

  private final static String USER = "user";
  private final static String PASSWORD = "pwd";

  private AgentSupportBuilder myBuilder;
  private GitAgentVcsSupport myVcsSupport;
  private MirrorManagerImpl myMirrorManager;
  private GitHttpServer myServer;
  private String myGitPath;
  private File myBuildDir;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myBuilder = new AgentSupportBuilder(myTempFiles);
    myVcsSupport = myBuilder.build();
    myMirrorManager = myBuilder.getMirrorManager();
    myGitPath = GitVersionProvider.getGitPath();
    myBuildDir = myTempFiles.createTempDir();
  }


  @Override
  @AfterMethod
  public void tearDown() {
    super.tearDown();
    if (myServer != null)
      myServer.stop();
  }


  @Test(dataProvider = "mirrorModes")
  public void should_include_username_into_url_when_asked(@NotNull MirrorMode mirrorMode) throws Exception {
    //need that in order to be able to disable new logic in case of any problems

    File repo = copyRepository(myTempFiles, dataFile("repo_for_fetch.1"), "repo.git");
    startGitServer(repo);
    VcsRootImpl root = createRoot();
    AgentRunningBuild build = createBuild(configParams(mirrorMode, PluginConfigImpl.EXCLUDE_USERNAME_FROM_HTTP_URL, "false"));
    checkout(root, build, "add81050184d3c818560bdd8839f50024c188586");

    if (mirrorMode != MirrorMode.DISABLED) {
      StoredConfig config = getMirrorConfig(root);
      then(config.getString("remote", "origin", "url")).contains(USER + "@");
    }

    StoredConfig config = getWorkingDirConfig();
    then(config.getString("remote", "origin", "url")).contains(USER + "@");
  }


  @Test(dataProvider = "mirrorModes")
  public void no_username_in_http_urls(@NotNull MirrorMode mirrorMode) throws Exception {
    File repo = copyRepository(myTempFiles, dataFile("repo_for_fetch.1"), "repo.git");
    startGitServer(repo);
    VcsRootImpl root = createRoot();
    AgentRunningBuild build = createBuild(configParams(mirrorMode));
    checkout(root, build, "add81050184d3c818560bdd8839f50024c188586");

    if (mirrorMode != MirrorMode.DISABLED) {
      StoredConfig config = getMirrorConfig(root);
      then(config.getString("remote", "origin", "url")).doesNotContain(USER + "@");
      then(config.getString("credential", config.getString("remote", "origin", "url"), "username")).isEqualTo(USER);
    }

    StoredConfig config = getWorkingDirConfig();
    then(config.getString("remote", "origin", "url")).doesNotContain(USER + "@");
    then(config.getString("credential", config.getString("remote", "origin", "url"), "username")).isEqualTo(USER);
  }


  @Test(dataProvider = "mirrorModes")
  public void no_username_in_http_urls_upgrade(@NotNull MirrorMode mirrorMode) throws Exception {
    //run first build to initialize fetch urls with usernames
    File repo = copyRepository(myTempFiles, dataFile("repo_for_fetch.1"), "repo.git");
    startGitServer(repo);
    VcsRootImpl root = createRoot();
    AgentRunningBuild build1 = createBuild(configParams(mirrorMode, PluginConfigImpl.EXCLUDE_USERNAME_FROM_HTTP_URL, "false"));
    checkout(root, build1, "add81050184d3c818560bdd8839f50024c188586");

    //update remote repo to cause fetch
    FileUtil.delete(repo);
    copyRepository(dataFile("repo_for_fetch.2"), repo);

    AgentRunningBuild build = createBuild(configParams(mirrorMode));
    checkout(root, build, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168");

    if (mirrorMode != MirrorMode.DISABLED) {
      StoredConfig config = getMirrorConfig(root);
      then(config.getString("remote", "origin", "url")).doesNotContain(USER + "@");
      then(config.getString("credential", config.getString("remote", "origin", "url"), "username")).isEqualTo(USER);
    }

    StoredConfig config = getWorkingDirConfig();
    then(config.getString("remote", "origin", "url")).doesNotContain(USER + "@");
    then(config.getString("credential", config.getString("remote", "origin", "url"), "username")).isEqualTo(USER);
  }


  private void checkout(@NotNull VcsRootImpl root, @NotNull AgentRunningBuild build, @NotNull String revision) throws VcsException {
    myVcsSupport.updateSources(root, CheckoutRules.DEFAULT, revision, myBuildDir, build, false);
  }


  @NotNull
  private StoredConfig getWorkingDirConfig() throws IOException {
    Repository r = new RepositoryBuilder().setWorkTree(myBuildDir).build();
    return r.getConfig();
  }


  @NotNull
  private StoredConfig getMirrorConfig(@NotNull VcsRootImpl root) throws IOException, VcsException {
    GitVcsRoot gitRoot = new GitVcsRoot(myMirrorManager, root, new URIishHelperImpl());
    File mirrorDir = myMirrorManager.getMirrorDir(gitRoot.getRepositoryFetchURL().toString());
    Repository r = new RepositoryBuilder().setGitDir(mirrorDir).build();
    return r.getConfig();
  }


  @NotNull
  private AgentRunningBuild createBuild(@NotNull Map<String, String> configParams) {
    return runningBuild()
      .sharedEnvVariable(Constants.TEAMCITY_AGENT_GIT_PATH, myGitPath)
      .sharedConfigParams(configParams)
      .withAgentConfiguration(myAgentConfiguration)
      .withCheckoutDir(myBuildDir)
      .build();
  }


  @NotNull
  private VcsRootImpl createRoot() {
    return vcsRoot()
      .withFetchUrl(myServer.getRepoUrl())
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername(USER)
      .withPassword(PASSWORD)
      .withBranch("master")
      .build();
  }


  private void startGitServer(@NotNull File repo) throws IOException {
    myServer = new GitHttpServer(myGitPath, repo);
    myServer.setCredentials(USER, PASSWORD);
    myServer.start();
  }


  @NotNull
  private Map<String, String> configParams(@NotNull MirrorMode mirrorMode, String... params) {
    Map<String, String> result = new HashMap<>();
    switch (mirrorMode) {
      case MIRROR:
        result.put(PluginConfigImpl.USE_MIRRORS, "true");
        break;
      case ALTERNATES:
        result.put(PluginConfigImpl.USE_ALTERNATES, "true");
    }
    if (params.length != 0)
      result.putAll(map(params));
    return result;
  }


  @DataProvider
  public static Object[][] mirrorModes() throws Exception {
    Object[][] result = new Object[MirrorMode.values().length][];
    int i = 0;
    for (MirrorMode mode : MirrorMode.values()) {
      result[i] = new Object[]{mode};
      i++;
    }
    return result;
  }

  private enum MirrorMode {
    DISABLED,
    MIRROR,
    ALTERNATES
  }
}
