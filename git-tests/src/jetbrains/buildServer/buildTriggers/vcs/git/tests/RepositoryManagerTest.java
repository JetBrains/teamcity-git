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
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertSame;

/**
 * @author dmitry.neverov
 */
@Test
public class RepositoryManagerTest {

  private TempFiles myTempFiles;
  private PluginConfigBuilder myPluginConfig;

  @BeforeMethod
  public void setUp() throws Exception {
    myTempFiles = new TempFiles();
    ServerPaths paths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    myPluginConfig = new PluginConfigBuilder(paths);
  }


  public void tearDown() {
    myTempFiles.cleanup();
  }


  private void should_use_same_dir_for_same_urls() throws Exception {
    RepositoryManager repositoryManager = getRepositoryManager();
    Repository noAuthRepository = repositoryManager.openRepository(new URIish("ssh://some.org/repository.git"));
    String path = noAuthRepository.getDirectory().getCanonicalPath();
    assertEquals(path, getRepositoryPath(repositoryManager, "ssh://some.org/repository.git"));
    assertEquals(path, getRepositoryPath(repositoryManager, "ssh://name@some.org/repository.git"));
    assertEquals(path, getRepositoryPath(repositoryManager, "ssh://name:pass@some.org/repository.git"));
    assertEquals(path, getRepositoryPath(repositoryManager, "ssh://other-name@some.org/repository.git"));
    assertEquals(path, getRepositoryPath(repositoryManager, "ssh://other-name:pass@some.org/repository.git"));
  }


  public void should_return_same_lock_for_files_point_to_same_dir() throws Exception {
    File dir1 = new File(".");
    RepositoryManager repositoryManager = getRepositoryManager();
    ReadWriteLock rmLock1 = repositoryManager.getRmLock(dir1);
    ReadWriteLock rmLock2 = repositoryManager.getRmLock(new File(".." + File.separator + dir1.getCanonicalFile().getName()));
    assertSame(rmLock1, rmLock2);
  }


  public void expired_dirs_should_not_include_map_file() throws Exception {
    myPluginConfig.setMirrorExpirationTimeoutMillis(100);
    RepositoryManager repositoryManager = getRepositoryManager();
    repositoryManager.openRepository(new URIish("ssh://some.org/repository.1.git"));
    repositoryManager.openRepository(new URIish("ssh://some.org/repository.2.git"));
    repositoryManager.openRepository(new URIish("ssh://some.org/repository.3.git"));
    Thread.sleep(200);
    File cachesDir = myPluginConfig.build().getCachesDir();
    List<File> expiredDirs = repositoryManager.getExpiredDirs();
    assertEquals(3, expiredDirs.size());
    assertFalse(expiredDirs.contains(new File(cachesDir, "map")));
  }


  private String getRepositoryPath(@NotNull RepositoryManager repositoryManager, @NotNull final String url) throws Exception {
    Repository repository = repositoryManager.openRepository(new URIish(url));
    return repository.getDirectory().getCanonicalPath();
  }

  private RepositoryManager getRepositoryManager() {
    ServerPluginConfig config = myPluginConfig.build();
    MirrorManager mirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl());
    return new RepositoryManagerImpl(config, mirrorManager);
  }
}
