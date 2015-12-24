/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.jcraft.jsch.JSchException;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.PluginConfigBuilder.pluginConfig;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

@Test
public class TestConnectionTest extends BaseRemoteRepositoryTest {

  private GitVcsSupport myGit;
  private ServerPaths myPaths;

  public TestConnectionTest() {
    super("repo.git");
  }

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    myGit = gitSupport().withServerPaths(myPaths).build();
  }

  @AfterMethod
  public void tearDown() {
    super.tearDown();
  }


  public void should_failed_when_branch_not_found() throws Exception {
    try {
      VcsRoot root = vcsRoot().withFetchUrl(getRemoteRepositoryUrl("repo.git")).withBranch("no-such-branch").build();
      myGit.testConnection(root);
      fail("Test connection should fail for unknown branch");
    } catch (VcsException ex) {
      assertTrue(true);
    }
  }


  @TestFor(issues = "TW-9933")
  public void test_not_existing_local_repository() throws Exception {
    File notExisting = new File(myTempFiles.createTempDir(), "not-existing");
    String url = GitUtils.toURL(notExisting);
    VcsRootImpl root = vcsRoot().withFetchUrl(url).build();
    try {
      myGit.testConnection(root);
      fail("Should throw an exception for not-existing repository");
    } catch (VcsException e) {
      assertTrue(e.getMessage().contains("Cannot access the '" + url + "' repository"));
      assertFalse(e.getMessage().endsWith("\n"));
    }
  }


  public void testConnection_should_throw_exception_for_anonymous_git_url_with_username() throws Exception {
    ServerPluginConfig config = pluginConfig().withDotBuildServerDir(myTempFiles.createTempDir())
      .setFetchTimeout(2).setCurrentStateTimeoutSeconds(2).build();
    myGit = gitSupport().withServerPaths(myPaths).withPluginConfig(config).build();

    String url = "git://git@some.org/repository";
    VcsRootImpl root = vcsRoot().withFetchUrl(url).build();
    try {
      myGit.testConnection(root);
      fail("should fail, because native git fails for such url");
    } catch (VcsException e) {
      assertTrue(e.getMessage().contains("Incorrect url " + url + ": anonymous git url should not contain a username"));
    }

    //other operations should fail with another error message,
    //that means old roots that have such urls and use server-side checkout will still work
    try {
      myGit.collectChanges(root, "f3f826ce85d6dad25156b2d7550cedeb1a422f4c", "ce6044093939bb47283439d97a1c80f759669ff5", CheckoutRules.DEFAULT);
      fail("should fail, because no such root exists");
    } catch (VcsException e) {
      assertFalse(e.getMessage().contains("Incorrect url " + url + ": anonymous git url should not contain a username"));
    }
  }


  public void testConnection_should_validate_branchSpec() throws Exception {
    VcsRootImpl root = vcsRoot()
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("master")
      .withBranchSpec("+:/refs/heads/*")
      .build();

    try {
      myGit.testConnection(root);
      fail("Test connection should validate branchSpec");
    } catch (VcsException e) {
      assertTrue(e.getMessage().contains("pattern should not start with /"));
    }
  }


  public void testConnection_should_not_be_blocked_by_long_fetch() throws Exception {
    PluginConfigBuilder config = pluginConfig().withDotBuildServerDir(myTempFiles.createTempDir()).setSeparateProcessForFetch(false);

    final Semaphore fetchStarted = new Semaphore(1);
    final Semaphore fetchCanFinish = new Semaphore(1);

    final GitVcsSupport git = gitSupport()
      .withBeforeFetchHook(new Runnable() {
        public void run() {
          fetchStarted.release();
          fetchCanFinish.acquireUninterruptibly();
        }
      })
      .withPluginConfig(config)
      .build();

    final VcsRoot root = vcsRoot().withFetchUrl(getRemoteRepositoryUrl("repo.git")).build();

    fetchStarted.acquireUninterruptibly();//don't allow fetch to start
    fetchCanFinish.acquireUninterruptibly();//don't allow fetch to finish

    Thread longFetch = new Thread(new Runnable() {
      public void run() {
        try {
          git.collectChanges(root, "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7", CheckoutRules.DEFAULT);
        } catch (VcsException e) {
          e.printStackTrace();
        }
      }
    });
    longFetch.start();

    Assert.assertTrue(fetchStarted.tryAcquire(10, TimeUnit.SECONDS));
    git.testConnection(root);//test connection during long fetch
    fetchCanFinish.release();//allow fetch to finish
  }


  @DataProvider(name = "common-github-auth-errors")
  public static Object[][] commonGithubAuthErrors() {
    return new Object[][] {
      new Object[] { "Auth fail" },
      new Object[] { "session is down" }
    };
  }


  @TestFor(issues = "TW-24074")
  @Test(dataProvider = "common-github-auth-errors")
  public void wrong_github_username(@NotNull final String error) {
    TransportFactory factoryWithGivenError = new TransportFactory() {
      public Transport createTransport(@NotNull Repository r,
                                       @NotNull URIish url,
                                       @NotNull AuthSettings authSettings) throws NotSupportedException, VcsException, TransportException {
        return createTransport(r, url, authSettings, 60/*doesn't matter*/);
      }

      public Transport createTransport(@NotNull Repository r,
                                       @NotNull URIish url,
                                       @NotNull AuthSettings authSettings,
                                       int timeout) throws NotSupportedException, VcsException, TransportException {
        throw new TransportException(url.toString() + ": " + error, new JSchException(error));
      }
    };

    myGit = gitSupport().withTransportFactory(factoryWithGivenError)
      .withServerPaths(myPaths)
      .build();

    String wrongUsername = "user";
    VcsRootImpl root = vcsRoot()
      .withFetchUrl("git@github.com:user/repo.git")
      .withBranch("master")
      .withAuthMethod(AuthenticationMethod.PRIVATE_KEY_DEFAULT)
      .withUsername(wrongUsername)
      .build();

    try {
      myGit.testConnection(root);
      fail("should fail during transport creation");
    } catch (VcsException e) {
      assertTrue(e.getMessage(),
                 e.getMessage().contains("Wrong username: '" + wrongUsername + "', GitHub expects a username 'git'"));
    }
  }
}
