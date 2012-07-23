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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.log.Log4jFactory;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.RepositoryState;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.vcs.RepositoryStateFactory.createSingleVersionRepositoryState;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

@Test
public class MapFullPathTest {

  static {
    Logger.setFactory(new Log4jFactory());
  }

  private TempFiles myTempFiles;
  private File myRemoteRepositoryDir;
  private GitVcsSupport myGit;
  private VcsRoot myRoot;

  @BeforeMethod
  public void setUp() throws IOException {
    myTempFiles = new TempFiles();
    myRemoteRepositoryDir = myTempFiles.createTempDir();
    copyRepository(dataFile("repo_for_fetch.1"), myRemoteRepositoryDir);
    ServerPaths paths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    ResetCacheRegister myResetCacheManager = new ResetCacheRegister();
    ServerPluginConfig config = new PluginConfigBuilder(paths).build();
    TransportFactory transportFactory = new TransportFactoryImpl(config);
    FetchCommand fetchCommand = new FetchCommandImpl(config, transportFactory);
    MirrorManager mirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl());
    RepositoryManager repositoryManager = new RepositoryManagerImpl(config, mirrorManager);
    myGit = new GitVcsSupport(config, myResetCacheManager, transportFactory, fetchCommand, repositoryManager, null);
    myRoot = vcsRoot().withFetchUrl(myRemoteRepositoryDir.getAbsolutePath()).build();
  }

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }


  @TestFor(issues = "TW-21185")
  @Test
  public void mapFullPath_should_report_up_to_date_info() throws Exception {
    RepositoryState state0 = createSingleVersionRepositoryState("a7274ca8e024d98c7d59874f19f21d26ee31d41d");
    RepositoryState state1 = myGit.getCurrentState(myRoot);
    myGit.getCollectChangesPolicy().collectChanges(myRoot, state0, state1, CheckoutRules.DEFAULT);

    Collection<String> paths = myGit.mapFullPath(new VcsRootEntry(myRoot, CheckoutRules.DEFAULT), "d47dda159b27b9a8c4cee4ce98e4435eb5b17168||.");
    assertTrue(paths.isEmpty());

    remoteRepositoryUpdated();

    RepositoryState state2 = myGit.getCurrentState(myRoot);
    myGit.getCollectChangesPolicy().collectChanges(myRoot, state1, state2, CheckoutRules.DEFAULT);//now we have d47dda159b27b9a8c4cee4ce98e4435eb5b17168
    paths = myGit.mapFullPath(new VcsRootEntry(myRoot, CheckoutRules.DEFAULT), "d47dda159b27b9a8c4cee4ce98e4435eb5b17168||.");
    assertFalse("mapFullPath returns outdated info", paths.isEmpty());
  }


  private void remoteRepositoryUpdated() throws IOException {
    FileUtil.delete(myRemoteRepositoryDir);
    copyRepository(dataFile("repo_for_fetch.2"), myRemoteRepositoryDir);
  }
}
