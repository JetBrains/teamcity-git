/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.commitInfo.GitCommitsInfoBuilder;
import jetbrains.buildServer.buildTriggers.vcs.git.patch.BulkPatchBuilderImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.patch.BulkPatchBuilderImpl.BulkPatchBuilder;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.CommitsInfoBuilder;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.vcs.api.CommitInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 */
public class BulkPatchBuilderTest extends BaseTestCase {
  private TempFiles myTempFiles = new TempFiles();

  private File myRepositoryDir;
  private GitVcsSupport myGit;
  private GitCommitsInfoBuilder myCommitSupport;
  private BulkPatchBuilderImpl myBulkBuilder;

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }


  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();
    ServerPaths myPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    PluginConfigBuilder config = new PluginConfigBuilder();

    GitSupportBuilder builder = gitSupport().withServerPaths(myPaths);
    myGit = builder.build();
    myCommitSupport = new GitCommitsInfoBuilder(myGit);
    myBulkBuilder = new BulkPatchBuilderImpl(builder.getPluginConfig(), myGit);

    myRepositoryDir = myTempFiles.createTempDir();
    File masterRep = dataFile("repo.git");
    FileUtil.copyDir(masterRep, myRepositoryDir);
  }


  @Test
  public void test_merged_patch() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl(GitUtils.toURL(myRepositoryDir)).withBranch("master").build();
    final List<String> commits = new ArrayList<String>();

    myCommitSupport.collectCommits(root, CheckoutRules.DEFAULT, new CommitsInfoBuilder.CommitsConsumer() {
      public void consumeCommit(@NotNull CommitInfo commit) {
        commits.add(commit.getVersion());
      }
    });


    final ArrayList<String> log = new ArrayList<String>();
    myBulkBuilder.buildIncrementalPatch(root, CheckoutRules.DEFAULT, commits, null, patcher(log));

    Assert.assertTrue(log.size() > 0);
  }


  @NotNull
  private BulkPatchBuilder patcher(@NotNull final List<String> log) {
    return (BulkPatchBuilder)Proxy.newProxyInstance(
      getClass().getClassLoader(),
      new Class<?>[]{BulkPatchBuilder.class},
      new InvocationHandler() {
        public Object invoke(final Object o,
                             final Method method,
                             final Object[] objects) throws Throwable {

          final String message = method.getName() + " " + Arrays.toString(objects);
          log.add(message);
          System.out.println("PATCH: " + message);
          return null;
        }
      });
  }

}
