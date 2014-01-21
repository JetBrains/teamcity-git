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

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

@Test
public class SubmoduleErrorsTest {

  private TempFiles myTempFiles = new TempFiles();
  private GitVcsSupport myGitSupport;
  private File myMainRepo;
  private String myMainRepoUrl;

  @BeforeMethod
  public void setUp() throws IOException {
    File dotBuildServer = myTempFiles.createTempDir();
    ServerPaths serverPaths = new ServerPaths(dotBuildServer.getAbsolutePath());
    myGitSupport = gitSupport().withServerPaths(serverPaths).build();
    File tmpDir = myTempFiles.createTempDir();
    myMainRepo = new File(tmpDir, "main");
    myMainRepoUrl = myMainRepo.getCanonicalPath();
    File mySubmoduleRepo = new File(tmpDir, "sub");
    copyRepository(dataFile("submodule.errors/main"), myMainRepo);
    copyRepository(dataFile("submodule.errors/sub"), mySubmoduleRepo);
  }

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }


  public void cannot_fetch_submodule() throws Exception {
    String submodulePath = "sub";
    String wrongSubmoduleUrl = new File(myMainRepo.getParent(), "sub2").getCanonicalPath();
    String brokenCommit = "9c328ea69b41ad2bfa162c72fd52cf87376225b7";
    VcsRoot root = vcsRoot().withFetchUrl(myMainRepoUrl).withSubmodulePolicy(SubmodulesCheckoutPolicy.CHECKOUT).build();
    try {
      myGitSupport.getCollectChangesPolicy().collectChanges(root,
                                                            "6dbc05799659295e480894e367f4159d57fba30d",
                                                            brokenCommit,
                                                            CheckoutRules.DEFAULT);
      fail("Should fail due to incorrect submodule url");
    } catch (VcsException e) {
      String msg = e.getMessage();
      assertTrue(msg.contains(
        "Cannot fetch the '" + wrongSubmoduleUrl
        + "' repository used as a submodule at the '" + submodulePath
        + "' path in the '" + myMainRepoUrl
        + "' repository in the " + brokenCommit + " commit"
      ));
    }
  }
}
