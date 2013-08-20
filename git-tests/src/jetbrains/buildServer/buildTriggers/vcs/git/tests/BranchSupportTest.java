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
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.ModificationData;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author dmitry.neverov
 */
@Test
public class BranchSupportTest {
  private static final TempFiles ourTempFiles = new TempFiles();
  protected File myRepositoryDir;
  private ServerPaths myServerPaths;

  @BeforeMethod
  public void setUp() throws IOException {
    File teamcitySystemDir = ourTempFiles.createTempDir();
    myServerPaths = new ServerPaths(teamcitySystemDir.getAbsolutePath(), teamcitySystemDir.getAbsolutePath(), teamcitySystemDir.getAbsolutePath());
    File masterRep = dataFile("repo.git");
    myRepositoryDir = ourTempFiles.createTempDir();
    FileUtil.copyDir(masterRep, myRepositoryDir);
  }

  @AfterMethod
  public void tearDown() {
    ourTempFiles.cleanup();
  }


  /**
   * o ee886e4adb70fbe3bdc6f3f6393598b3f02e8009 (change1)
   * |
   * |
   * |  o 1391281d33a83a7205f2f05d3eb64c349c636e87 (change2)
   * |  |
   * | /
   * |/
   * o f3f826ce85d6dad25156b2d7550cedeb1a422f4c
   * |
   * |
   * |
   */
  public void test() throws VcsException {
    VcsRoot originalRoot = vcsRoot().withFetchUrl(GitUtils.toURL(myRepositoryDir)).withBranch("master").build();
    VcsRoot substitutionRoot = vcsRoot().withFetchUrl(GitUtils.toURL(myRepositoryDir)).withBranch("personal-branch1").build();

    final String originalRootVersion = "3b9fbfbb43e7edfad018b482e15e7f93cca4e69f";
    final String substitutionRootVersion = "1391281d33a83a7205f2f05d3eb64c349c636e87";

    GitVcsSupport vcsSupport = gitSupport().withServerPaths(myServerPaths).build();
    List<ModificationData> changes = vcsSupport.collectChanges(originalRoot, originalRootVersion, substitutionRoot, substitutionRootVersion, CheckoutRules.DEFAULT);

    assertEquals(1, changes.size());
    assertEquals(substitutionRoot, changes.get(0).getVcsRootObject());
    assertEquals(substitutionRootVersion, changes.get(0).getVersion());
  }
}
