/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

@Test
public class TestConnectionTest extends BaseRemoteRepositoryTest {

  private GitVcsSupport myGit;

  public TestConnectionTest() {
    super("repo.git");
  }

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    ServerPaths paths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    myGit = gitSupport().withServerPaths(paths).build();
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
    VcsRootImpl root = vcsRoot().withFetchUrl(GitUtils.toURL(notExisting)).build();
    try {
      myGit.testConnection(root);
      fail("Should throw an exception for not-existing repository");
    } catch (VcsException e) {
      assertTrue(e.getMessage().contains("Cannot access repository"));
      assertFalse(e.getMessage().endsWith("\n"));
    }
  }


  public void testConnection_should_throw_exception_for_anonymous_git_url_with_username() throws Exception {
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


  public void should_not_fail_when_password_is_empty() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl("http://some.org/repository")
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername("user")
      .build();
    myGit.testConnection(root);
  }
}
