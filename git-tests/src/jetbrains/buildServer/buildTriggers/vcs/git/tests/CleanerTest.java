/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;

/**
 * @author dmitry.neverov
 */
public class CleanerTest extends BaseTestCase {

  private static final TempFiles ourTempFiles = new TempFiles();
  private ServerPaths myServerPaths;
  private Cleaner myCleaner;
  private ScheduledExecutorService myCleanExecutor;
  private VcsManager myVcsManager;
  private Mockery myContext;

  @BeforeMethod
  public void setUp() throws IOException {
    File dotBuildServer = ourTempFiles.createTempDir();
    myServerPaths = new ServerPaths(dotBuildServer.getAbsolutePath(), dotBuildServer.getAbsolutePath(), dotBuildServer.getAbsolutePath());
    myCleanExecutor = Executors.newSingleThreadScheduledExecutor();
    myContext = new Mockery();
    final SBuildServer server = myContext.mock(SBuildServer.class);
    myVcsManager = myContext.mock(VcsManager.class);
    myContext.checking(new Expectations() {{
      allowing(server).getExecutor(); will(returnValue(myCleanExecutor));
      allowing(server).getVcsManager(); will(returnValue(myVcsManager));
    }});
    GitVcsSupport gitSupport = new GitVcsSupport(myServerPaths, null);
    myCleaner = new Cleaner(server, EventDispatcher.create(BuildServerListener.class), myServerPaths, gitSupport);
  }

  @AfterMethod
  public void tearDown() {
    ourTempFiles.cleanup();
  }

  @Test
  public void test_clean() throws VcsException, InterruptedException {
    System.setProperty("teamcity.server.git.gc.enabled", String.valueOf(true));
    if (System.getenv(Constants.GIT_PATH_ENV) != null)
      System.setProperty("teamcity.server.git.executable.path", System.getenv(Constants.GIT_PATH_ENV));

    final VcsRoot root = getVcsRoot();
    GitVcsSupport vcsSupport = new GitVcsSupport(myServerPaths, null);
    vcsSupport.getCurrentVersion(root);//it will create dir in cache directory
    File repositoryDir = getRepositoryDir(root);
    File gitCacheDir = new File(myServerPaths.getCachesDir(), "git");
    generateGarbage(gitCacheDir);

    final SVcsRoot usedRoot = createSVcsRootFor(root);
    myContext.checking(new Expectations() {{
      allowing(myVcsManager).findRootsByVcsName(Constants.VCS_NAME); will(returnValue(Collections.singleton(usedRoot)));
    }});
    invokeClean();

    File[] files = gitCacheDir.listFiles();
    assertEquals(1, files.length);
    assertEquals(repositoryDir, files[0]);

    vcsSupport.getCurrentVersion(root);//check that repository is fine after git gc
  }

  private void invokeClean() throws InterruptedException {
    myCleaner.cleanupStarted();
    myCleanExecutor.shutdown();
    myCleanExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
  }

  private File getRepositoryDir(VcsRoot root) throws VcsException {
    Settings settings = new Settings(root, myServerPaths.getCachesDir());
    return settings.getRepositoryPath();
  }

  private void generateGarbage(File dir) {
    for (int i = 0; i < 10; i++) {
      new File(dir, "git-AHAHAHA"+i+".git").mkdir();
    }
  }

  private VcsRoot getVcsRoot() {
    VcsRootImpl root = new VcsRootImpl(1, Constants.VCS_NAME);
    root.addProperty(Constants.FETCH_URL, GitUtils.toURL(dataFile("repo.git")));
    root.addProperty(Constants.BRANCH_NAME, "master");
    return root;
  }

  private SVcsRoot createSVcsRootFor(final VcsRoot root) {
    final SVcsRoot result = myContext.mock(SVcsRoot.class);
    myContext.checking(new Expectations() {{
      allowing(myVcsManager).findRootsByVcsName(Constants.VCS_NAME); will(returnValue(Collections.singleton(result)));
      allowing(result).getProperty(Constants.PATH); will(returnValue(root.getProperty(Constants.PATH)));
      allowing(result).getProperty(Constants.AUTH_METHOD); will(returnValue(root.getProperty(Constants.AUTH_METHOD)));
      allowing(result).getProperty(Constants.FETCH_URL); will(returnValue(root.getProperty(Constants.FETCH_URL)));
      allowing(result).getProperty(Constants.IGNORE_KNOWN_HOSTS); will(returnValue(root.getProperty(Constants.IGNORE_KNOWN_HOSTS)));
      allowing(result).getProperties(); will(returnValue(root.getProperties()));
    }});
    return result;
  }
}
