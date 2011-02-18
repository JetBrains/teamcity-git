/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.Settings;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import junit.framework.TestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author dmitry.neverov
 */
public class SettingsTest extends TestCase {

  private static final TempFiles ourTempFiles = new TempFiles();
  private ServerPaths myServerPaths;


  @BeforeMethod
  public void setUp() throws IOException {
    String dotBuildServerPath = ourTempFiles.createTempDir().getAbsolutePath();
    myServerPaths = new ServerPaths(dotBuildServerPath, dotBuildServerPath, dotBuildServerPath);
  }


  @AfterMethod
  public void tearDown() {
    ourTempFiles.cleanup();
  }


  /**
   * On the server ServerPaths.getCachesDir() returns path under with all plugins store their caches,
   * so we create dir git and store bare repositories at cachesDir/git/bare.repository.git. On the agent
   * BuildAgentConfiguration.getCacheDirectory(key) returns caches dir in which only our plugin stores its data.
   * Let's always store bare repositories directly under provided caches dir.
   */
  @Test
  public void bare_repository_located_directly_under_provide_caches_dir() throws VcsException {
    VcsRoot root = createRoot();
    Settings settings = new Settings(root, myServerPaths.getCachesDir());
    File bareRepositoryDir = settings.getRepositoryDir();
    assertEquals(myServerPaths.getCachesDir(), bareRepositoryDir.getParentFile().getAbsolutePath());
  }


  private VcsRoot createRoot() {
    VcsRootImpl root = new VcsRootImpl(1, Constants.VCS_NAME);
    root.addProperty(Constants.FETCH_URL, "git://some.org/repository.git");
    root.addProperty(Constants.BRANCH_NAME, "master");
    return root;
  }

}
