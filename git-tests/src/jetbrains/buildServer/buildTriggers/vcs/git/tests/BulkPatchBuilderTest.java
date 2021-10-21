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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.GitFetchService;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.commitInfo.GitCommitsInfoBuilder;
import jetbrains.buildServer.buildTriggers.vcs.git.patch.BulkPatchBuilderImpl;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.BulkPatchService.BulkPatchBuilder;
import jetbrains.buildServer.vcs.BulkPatchService.BulkPatchBuilderRequest;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.CommitsInfoBuilder;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import jetbrains.vcs.api.CommitInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

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
    GitSupportBuilder builder = gitSupport().withServerPaths(myPaths);
    myGit = builder.build();
    myCommitSupport = new GitCommitsInfoBuilder(myGit, new GitFetchService(myGit));
    myBulkBuilder = new BulkPatchBuilderImpl(builder.getPluginConfig(), myGit, builder.getTransportFactory());

    myRepositoryDir = myTempFiles.createTempDir();
    File masterRep = dataFile("repo.git");
    FileUtil.copyDir(masterRep, myRepositoryDir);
  }


  @Test
  public void test_merged_patch() throws Exception {
    final VcsRoot root = vcsRoot().withFetchUrl(GitUtils.toURL(myRepositoryDir)).withBranch("master").build();

    final ArrayList<String> log = new ArrayList<String>();
    final BulkPatchBuilder patcher = patcher(new PatchLogger() {
      public void log(@NotNull final String message) {
        log.add(message);
      }
    });

    runFullPatch(root, patcher);
    Assert.assertTrue(log.size() > 0);
  }

  @Test(enabled = false)
  public void test_local_idea() throws Exception {
    ///does not work for real repository: Fetcher call is mostly endless
    final String ultimate_local = "E:\\Work\\idea-ultimate\\.git";
    VcsRoot root = vcsRoot()
      .withFetchUrl(ultimate_local)
      .withRepositoryPathOnServer(ultimate_local)
      .withBranch("master")
      .build();

    final BulkPatchBuilder patcher = patcher(new PatchLogger() {
      public void log(@NotNull final String message) {

      }
    });

    runFullPatch(root, patcher);
  }

  private void runFullPatch(@NotNull final VcsRoot root,
                            @NotNull final BulkPatchBuilder patcher) throws VcsException, IOException {
    final List<String> commits = new ArrayList<String>();

    myCommitSupport.collectCommits(root, CheckoutRules.DEFAULT, new CommitsInfoBuilder.CommitsConsumer() {
      public void consumeCommit(@NotNull CommitInfo commit) {
        commits.add(commit.getVersion());
      }
    });

    Assert.assertTrue(commits.size() > 0);

    final List<BulkPatchBuilderRequest> request = new ArrayList<BulkPatchBuilderRequest>();
    String prev = commits.get(0);
    for (final String commit : commits) {
      final String base = prev;
      prev = commit;
      request.add(new BulkPatchBuilderRequest() {
        @Nullable
        public String getFromVersion() {
          return base;
        }

        @NotNull
        public String getToVersion() {
          return commit;
        }

        @Override
        public String toString() {
          return getFromVersion() + "->" + getToVersion();
        }
      });
    }

    myBulkBuilder.buildPatches(root, CheckoutRules.DEFAULT, request, patcher);
  }

  private interface PatchLogger {
    void log(@NotNull String message);
  }

  @NotNull
  private BulkPatchBuilder patcher(@NotNull final PatchLogger log) {
    return (BulkPatchBuilder)Proxy.newProxyInstance(
      getClass().getClassLoader(),
      new Class<?>[]{BulkPatchBuilder.class, PatchBuilder.class},
      new InvocationHandler() {
        public Object invoke(final Object o,
                             final Method method,
                             final Object[] objects) throws Throwable {

          final String message = method.getName() + " " + Arrays.toString(objects);
          log.log(message);

          if (objects != null) {
            for (Object object : objects) {
              if (object instanceof Closeable) {
                FileUtil.close((Closeable)object);
              }
            }
          }

          if (method.getName().equals("toString")) return "mock!";

          if (method.getName().equals("startPatch")) {
            System.out.println("PATCH:> " + message);
            return o;
          }

          return null;
        }
      });
  }

}
