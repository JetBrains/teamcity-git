/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.transport.URIish;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author dmitry.neverov
 */
@Test
public class GitVcsRootTest {

  private MirrorManager myMirrorManager;
  private File myDefaultCachesDir;

  @BeforeMethod
  public void setUp() throws IOException {
    new TeamCityProperties() {{setModel(new BasePropertiesModel() {});}};
    TempFiles tempFiles = new TempFiles();
    myDefaultCachesDir = tempFiles.createTempDir();
    ServerPaths serverPaths = new ServerPaths(myDefaultCachesDir.getAbsolutePath());
    ServerPluginConfig config = new PluginConfigBuilder(serverPaths).build();
    myMirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl());
  }

  public void fetch_url_for_repository_in_local_filesystem_should_not_contain_password() throws Exception {
    String pathInLocalFS = "/path/in/local/fs";
    VcsRoot root = vcsRoot().withFetchUrl(pathInLocalFS)
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername("user")
      .withPassword("pass")
      .build();
    GitVcsRoot s = new GitVcsRoot(myMirrorManager, root);
    assertEquals(new URIish(pathInLocalFS), s.getRepositoryFetchURL());
  }

  public void cred_prod() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl("git://git@some.org/repository.git").build();
    GitVcsRoot gitRoot = new GitVcsRoot(myMirrorManager, root);
    assertNull("User is not stripped from the url with anonymous protocol", gitRoot.getRepositoryFetchURL().getUser());
  }

  @TestFor(issues = "TW-36401")
  public void disabling_custom_clones() throws Exception {
    File cloneDir = new File("");
    VcsRoot root = vcsRoot().withRepositoryPathOnServer(cloneDir.getAbsolutePath()).withFetchUrl("http://some.org/repo").build();
    GitVcsRoot gitRoot1 = new GitVcsRoot(myMirrorManager, root);
    assertEquals(cloneDir.getAbsoluteFile(), gitRoot1.getRepositoryDir());
    try {
      System.setProperty(Constants.CUSTOM_CLONE_PATH_ENABLED, "false");
      GitVcsRoot gitRoot2 = new GitVcsRoot(myMirrorManager, root);
      assertTrue(FileUtil.isAncestor(myDefaultCachesDir, gitRoot2.getRepositoryDir(), true));
    } finally {
      System.getProperties().remove(Constants.CUSTOM_CLONE_PATH_ENABLED);
    }
  }
}
