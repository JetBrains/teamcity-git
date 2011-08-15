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

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsRoot;
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
  private GitVcsSupport mySupport;
  private PluginConfigBuilder myConfigBuilder;

  @BeforeMethod
  public void setUp() throws IOException {
    File dotBuildServer = ourTempFiles.createTempDir();
    myServerPaths = new ServerPaths(dotBuildServer.getAbsolutePath(), dotBuildServer.getAbsolutePath(), dotBuildServer.getAbsolutePath());
    myConfigBuilder = new PluginConfigBuilder(myServerPaths);
    myConfigBuilder.setRunNativeGC(true);
    if (System.getenv(Constants.GIT_PATH_ENV) != null)
      myConfigBuilder.setPathToGit(System.getenv(Constants.GIT_PATH_ENV));
    myCleanExecutor = Executors.newSingleThreadScheduledExecutor();
    myContext = new Mockery();
    final SBuildServer server = myContext.mock(SBuildServer.class);
    myVcsManager = myContext.mock(VcsManager.class);
    myContext.checking(new Expectations() {{
      allowing(server).getExecutor(); will(returnValue(myCleanExecutor));
      allowing(server).getVcsManager(); will(returnValue(myVcsManager));
    }});
    ServerPluginConfig config = new PluginConfigImpl(myServerPaths);
    TransportFactory transportFactory = new TransportFactoryImpl(config);
    FetchCommand fetchCommand = new FetchCommandImpl(config, transportFactory);
    mySupport = new GitVcsSupport(config, transportFactory, fetchCommand, null);
    myCleaner = new Cleaner(server, EventDispatcher.create(BuildServerListener.class), config, mySupport);
  }

  @AfterMethod
  public void tearDown() {
    ourTempFiles.cleanup();
  }


  @Test
  public void test_clean() throws VcsException, InterruptedException {
    final VcsRoot root = GitTestUtil.getVcsRoot();
    mySupport.getCurrentVersion(root);//it will create dir in cache directory
    File repositoryDir = getRepositoryDir(root);
    File gitCacheDir = new File(myServerPaths.getCachesDir(), "git");
    generateGarbage(gitCacheDir);

    myContext.checking(new Expectations() {{
      allowing(myVcsManager).findRootsByVcsName(Constants.VCS_NAME); will(returnValue(Collections.singleton(root)));
    }});
    invokeClean();

    File[] files = gitCacheDir.listFiles();
    assertEquals(1, files.length);
    assertEquals(repositoryDir, files[0]);

    mySupport.getCurrentVersion(root);//check that repository is fine after git gc
  }


  //TW-10401
  //if any usable VCS roots have fetch url with unresolved parameters we should not
  //remove unused directories, otherwise we will delete a directory of a usable
  //VCS root with resolved parameters
  @Test
  public void should_not_remove_unused_dirs_if_root_url_has_parameters() throws Exception {
    final VcsRoot root = new VcsRootBuilder().fetchUrl("%repository.url%").build();

    File gitCacheDir = new File(myServerPaths.getCachesDir(), "git");
    generateGarbage(gitCacheDir);

    myContext.checking(new Expectations() {{
      allowing(myVcsManager).findRootsByVcsName(Constants.VCS_NAME); will(returnValue(Collections.singleton(root)));
    }});
    invokeClean();

    File[] files = gitCacheDir.listFiles();
    assertEquals(10, files.length);
  }


  private void invokeClean() throws InterruptedException {
    myCleaner.cleanupStarted();
    myCleanExecutor.shutdown();
    myCleanExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
  }

  private File getRepositoryDir(VcsRoot root) throws VcsException {
    Settings settings = new Settings(root, mySupport.getCachesDir());
    return settings.getRepositoryDir();
  }

  private void generateGarbage(File dir) {
    dir.mkdirs();
    for (int i = 0; i < 10; i++) {
      new File(dir, "git-AHAHAHA"+i+".git").mkdir();
    }
  }
}
