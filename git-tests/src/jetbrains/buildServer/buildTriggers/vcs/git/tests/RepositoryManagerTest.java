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

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

import static org.assertj.core.api.BDDAssertions.then;
import static org.testng.AssertJUnit.*;

/**
 * @author dmitry.neverov
 */
@Test
public class RepositoryManagerTest {

  private TempFiles myTempFiles;
  private PluginConfigBuilder myPluginConfig;

  @BeforeMethod
  public void setUp() throws Exception {
    new TeamCityProperties() {{ setModel(new BasePropertiesModel() {});}};
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


  public void should_throw_exception_when_repository_configured_for_different_url() throws Exception {
    RepositoryManager repositoryManager = getRepositoryManager();
    File dir = repositoryManager.getMirrorDir("git://some.org/repo.git");
    repositoryManager.openRepository(new URIish("git://some.org/repo.git"));
    try {
      repositoryManager.openRepository(dir, new URIish("git://some.org/other-repo.git"));
      fail("Expect error, dir is used for another url");
    } catch (VcsException e) {
      assertTrue(true);
    }
  }


  public void should_throw_exception_when_repository_configured_for_different_url__custom_dir_case() throws Exception {
    RepositoryManager repositoryManager = getRepositoryManager();
    File customDir = myTempFiles.createTempDir();
    repositoryManager.openRepository(customDir, new URIish("git://some.org/repo.git"));
    try {
      repositoryManager.openRepository(customDir, new URIish("git://some.org/other-repo.git"));
      fail("Expect error, dir is used for another url");
    } catch (VcsException e) {
      assertTrue(true);
    }
  }


  public void should_create_repository_if_remote_is_not_specified() throws Exception {
    RepositoryManager repositoryManager = getRepositoryManager();
    File customDir = myTempFiles.createTempDir();
    URIish url = new URIish("git://some.org/repo.git");
    Repository r = repositoryManager.openRepository(customDir, url);
    r.getConfig().unset("teamcity", null, "remote");
    Repository r2 = repositoryManager.openRepository(customDir, url);
    then(r2.getConfig().getString("teamcity", null, "remote")).isEqualTo(url.toString());
  }


  public void should_create_repo_if_it_does_not_exist() throws Exception {
    RepositoryManager repositoryManager = getRepositoryManager();
    File dir = repositoryManager.getMirrorDir("git://some.org/repo.git");
    assertFalse(dir.exists());
    repositoryManager.openRepository(new URIish("git://some.org/repo.git"));
    assertTrue(dir.exists());
  }


  public void should_create_repo_in_custom_dir() throws Exception {
    RepositoryManager repositoryManager = getRepositoryManager();
    File customDir = myTempFiles.createTempDir();
    FileUtil.delete(customDir);
    assertFalse(customDir.exists());
    repositoryManager.openRepository(customDir, new URIish("git://some.org/repo.git"));
    assertTrue(customDir.exists());
  }


  public void should_release_repository_if_it_is_not_used_anymore() throws Exception {
    RepositoryManager repositoryManager = getRepositoryManager();
    Repository r1 = repositoryManager.openRepository(new URIish("git://some.org/repo.git"));
    repositoryManager.closeRepository(r1);
    Repository r2 = repositoryManager.openRepository(new URIish("git://some.org/repo.git"));
    assertNotSame(r1, r2);
  }


  public void should_not_keep_repo_opened_in_case_of_error() throws Exception {
    File customDir = myTempFiles.createTempDir();
    FileUtil.delete(customDir);
    RepositoryManager repositoryManager = getRepositoryManager();
    Repository r1 = repositoryManager.openRepository(customDir, new URIish("git://some.org/repo.git"));
    try {
      repositoryManager.openRepository(customDir, new URIish("git://some.org/other-repo.git"));
      fail("Expect error, dir is used for another url");
    } catch (VcsException e) {
      assertTrue(true);
    }
    repositoryManager.closeRepository(r1);
    Repository r2 = repositoryManager.openRepository(customDir, new URIish("git://some.org/repo.git"));
    assertNotSame(r1, r2);
  }


  public void should_reuse_opened_repositories() throws Exception {
    RepositoryManager repositoryManager = getRepositoryManager();
    Repository r1 = repositoryManager.openRepository(new URIish("git://some.org/repo.git"));
    Repository r2 = repositoryManager.openRepository(new URIish("git://some.org/repo.git"));
    assertSame(r1, r2);
    repositoryManager.closeRepository(r2);
    Repository r3 = repositoryManager.openRepository(new URIish("git://some.org/repo.git"));
    assertSame(r1, r3);
  }


  public void get_repository_in_dir_with_existing_config_without_teamcity_remote() throws Exception {
    File customDir = myTempFiles.createTempDir();
    Repository r = new RepositoryBuilder().setGitDir(customDir).setBare().build();
    assertNull(r.getConfig().getString("teamcity", null, "remote"));

    RepositoryManager repositoryManager = getRepositoryManager();
    Repository r2 = repositoryManager.openRepository(customDir, new URIish("git://some.org/repo.git"));
    assertEquals(customDir, r2.getDirectory());
    assertEquals("git://some.org/repo.git", r2.getConfig().getString("teamcity", null, "remote"));
  }


  private String getRepositoryPath(@NotNull RepositoryManager repositoryManager, @NotNull final String url) throws Exception {
    Repository repository = repositoryManager.openRepository(new URIish(url));
    return repository.getDirectory().getCanonicalPath();
  }

  private RepositoryManager getRepositoryManager() {
    ServerPluginConfig config = myPluginConfig.build();
    MirrorManager mirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl(), new RemoteRepositoryUrlInvestigatorImpl());
    return new RepositoryManagerImpl(config, mirrorManager);
  }
}
