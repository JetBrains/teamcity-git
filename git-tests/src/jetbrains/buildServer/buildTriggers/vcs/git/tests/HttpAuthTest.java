/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder.runningBuild;
import static org.testng.AssertJUnit.assertTrue;

@SuppressWarnings("ALL")
@Test(dataProviderClass = GitVersionProvider.class, dataProvider = "version")
public class HttpAuthTest extends BaseRemoteRepositoryTest {

  private AgentSupportBuilder myBuilder;
  private GitAgentVcsSupport myVcsSupport;
  private GitHttpServer myServer;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myBuilder = new AgentSupportBuilder(myTempFiles);
    myVcsSupport = myBuilder.build();
  }


  @Override
  @AfterMethod
  public void tearDown() {
    super.tearDown();
    if (myServer != null)
      myServer.stop();
  }


  @RequiredGitVersion(min = "2.9.0")
  @TestFor(issues = {"TW-46668", "TW-45991", "TW-46391"})
  public void disable_credential_helpers(@NotNull GitExec git) throws Exception {
    //Test checks that we disable credential helpers configured on machine

    File repo = copyRepository(myTempFiles, dataFile("repo_for_fetch.1"), "repo.git");

    Random r = new Random();
    final String user = "user";
    final String password = String.valueOf(r.nextInt(100));
    myServer = new GitHttpServer(git.getPath(), repo);
    myServer.setCredentials(user, password);
    myServer.start();

    VcsRootImpl root = vcsRoot()
      .withFetchUrl(myServer.getRepoUrl())
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername(user)
      .withPassword(password)
      .withBranch("master")
      .build();

    File buildDir = myTempFiles.createTempDir();
    AgentRunningBuild build = runningBuild()
      .sharedEnvVariable(Constants.TEAMCITY_AGENT_GIT_PATH, git.getPath())
      .sharedConfigParams(PluginConfigImpl.USE_ALTERNATES, "true")
      .build();

    //run first build to initialize mirror:
    Checkout checkout = new Checkout(root, "add81050184d3c818560bdd8839f50024c188586", buildDir, build);
    checkout.run(TimeUnit.SECONDS.toMillis(10));
    assertTrue(checkout.success());

    //update remote repo with new commits:
    FileUtil.delete(repo);
    copyRepository(dataFile("repo_for_fetch.2"), repo);

    //configure hanging credential helper for mirror:
    File mirrorDir = myBuilder.getMirrorManager().getMirrorDir(myServer.getRepoUrl().replaceAll("http://", "http://" + user + "@"));
    Repository mirror = new RepositoryBuilder().setGitDir(mirrorDir).build();
    StoredConfig config = mirror.getConfig();
    config.setString("credential", null, "helper", createHangingCredProvider(100).getCanonicalPath());
    config.save();

    //run build requiring a mirror update, hanging helper should be disabled and checkout should finish successfully
    checkout = new Checkout(root, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168", buildDir, build);
    checkout.run(TimeUnit.SECONDS.toMillis(10));
    assertTrue(checkout.success());
  }


  @TestFor(issues = "TW-51968")
  public void quote_in_password(@NotNull GitExec git) throws Exception {
    File repo = copyRepository(myTempFiles, dataFile("repo_for_fetch.1"), "repo.git");

    final String user = "user";
    final String password = "pass'word";
    myServer = new GitHttpServer(git.getPath(), repo);
    myServer.setCredentials(user, password);
    myServer.start();

    VcsRootImpl root = vcsRoot()
      .withFetchUrl(myServer.getRepoUrl())
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername(user)
      .withPassword(password)
      .withBranch("master")
      .build();

    File buildDir = myTempFiles.createTempDir();
    AgentRunningBuild build = runningBuild()
      .sharedEnvVariable(Constants.TEAMCITY_AGENT_GIT_PATH, git.getPath())
      .build();

    Checkout checkout = new Checkout(root, "add81050184d3c818560bdd8839f50024c188586", buildDir, build);
    checkout.run(TimeUnit.SECONDS.toMillis(10));
    assertTrue(checkout.success());
  }


    private class Checkout extends Thread {
    private final VcsRootImpl myRoot;
    private final String myRevision;
    private final File myBuildDir;
    private final AgentRunningBuild myBuild;
    private final AtomicBoolean mySuccess = new AtomicBoolean(false);
    Checkout(@NotNull VcsRootImpl root,
             @NotNull String revision,
             @NotNull File buildDir,
             @NotNull AgentRunningBuild build) {
      myRoot = root;
      myRevision = revision;
      myBuildDir = buildDir;
      myBuild = build;
    }


    public void run(long timeoutMillis) throws InterruptedException {
      start();
      join(timeoutMillis);
    }


    @Override
    public void run() {
      try {
        myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, myRevision, myBuildDir, myBuild, false);
        mySuccess.set(true);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    boolean success() {
      return mySuccess.get();
    }
  }


  @NotNull
  private File createHangingCredProvider(int waitTimeSeconds) throws IOException {
    File result;
    if (SystemInfo.isWindows) {
      result = myTempFiles.createTempFile("timeout " + waitTimeSeconds + " > null");
    } else {
      result = myTempFiles.createTempFile("sleep " + waitTimeSeconds);
    }
    result.setExecutable(true);
    return result;
  }
}
