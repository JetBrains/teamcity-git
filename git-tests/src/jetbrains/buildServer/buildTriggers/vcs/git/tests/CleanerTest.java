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

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;

@Test
public class CleanerTest extends BaseTestCase {

  private static final TempFiles ourTempFiles = new TempFiles();
  private Cleanup myCleanup;
  private GitVcsSupport mySupport;
  private RepositoryManager myRepositoryManager;
  private ServerPluginConfig myConfig;

  @BeforeMethod
  public void setUp() throws IOException {
    ServerPaths paths = new ServerPaths(ourTempFiles.createTempDir().getAbsolutePath());
    PluginConfigBuilder configBuilder = new PluginConfigBuilder(paths)
      .setRunNativeGC(true)
      .setMirrorExpirationTimeoutMillis(5000);

    if (System.getenv(Constants.TEAMCITY_AGENT_GIT_PATH) != null)
      configBuilder.setPathToGit(System.getenv(Constants.TEAMCITY_AGENT_GIT_PATH));

    myConfig = configBuilder.build();
    GitSupportBuilder gitBuilder = gitSupport().withPluginConfig(myConfig);
    mySupport = gitBuilder.build();
    myRepositoryManager = gitBuilder.getRepositoryManager();
    myCleanup = new Cleanup(myConfig, myRepositoryManager);
  }

  @AfterMethod
  public void tearDown() {
    ourTempFiles.cleanup();
  }


  public void test_clean() throws VcsException, InterruptedException {
    File baseMirrorsDir = myRepositoryManager.getBaseMirrorsDir();
    generateGarbage(baseMirrorsDir);

    Thread.sleep(2 * myConfig.getMirrorExpirationTimeoutMillis());

    final VcsRoot root = GitTestUtil.getVcsRoot();
    mySupport.getCurrentVersion(root);//it will create dir in cache directory
    File repositoryDir = getRepositoryDir(root);

    myCleanup.run();

    File[] files = baseMirrorsDir.listFiles(new FileFilter() {
      public boolean accept(File f) {
        return f.isDirectory();
      }
    });
    assertEquals(1, files.length);
    assertEquals(repositoryDir, files[0]);

    mySupport.getCurrentVersion(root);//check that repository is fine after git gc
  }


  private File getRepositoryDir(VcsRoot root) throws VcsException {
    GitVcsRoot gitRoot = new GitVcsRoot(myRepositoryManager, root);
    return gitRoot.getRepositoryDir();
  }

  private void generateGarbage(File dir) {
    dir.mkdirs();
    for (int i = 0; i < 10; i++) {
      new File(dir, "git-AHAHAHA"+i+".git").mkdir();
    }
  }
}
