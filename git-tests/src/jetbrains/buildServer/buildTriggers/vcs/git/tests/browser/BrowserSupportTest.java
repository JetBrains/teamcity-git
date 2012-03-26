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

package jetbrains.buildServer.buildTriggers.vcs.git.tests.browser;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.PluginConfigBuilder;
import jetbrains.buildServer.log.Log4jFactory;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.util.browser.Element;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.browser.DirectoryMatcher.directory;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.browser.DoesNotSupportInputStream.doesNotSupportInputStream;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.browser.DoesNotSupportSize.doesNotSupportSize;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.browser.HasContent.hasContentAsInRepository;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author dmitry.neverov
 */
@Test
public class BrowserSupportTest {

  static {
    Logger.setFactory(new Log4jFactory());
  }

  private TempFiles myTempFiles;
  private GitVcsSupport myGit;
  private File myRemoteRepositoryDir;

  @BeforeMethod
  public void setUp() throws Exception {
    myTempFiles = new TempFiles();
    ServerPaths serverPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    ServerPluginConfig config = new PluginConfigBuilder(serverPaths).build();
    TransportFactory transportFactory = new TransportFactoryImpl(config);
    FetchCommand fetchCommand = new FetchCommandImpl(config, transportFactory);
    MirrorManager mirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl());
    RepositoryManager repositoryManager = new RepositoryManagerImpl(config, mirrorManager);
    myGit = new GitVcsSupport(config, new ResetCacheRegister(), transportFactory, fetchCommand, repositoryManager, null);
    myRemoteRepositoryDir = new File(myTempFiles.createTempDir(), "repo.git");
    FileUtil.copyDir(dataFile("repo.git"), myRemoteRepositoryDir);
  }

  @AfterMethod
  public void tearDown() throws Exception {
    myTempFiles.cleanup();
  }


  public void browse_in_cloned_repository() throws Exception {
    VcsRoot vcsRoot = vcsRoot().withFetchUrl(myRemoteRepositoryDir.getAbsolutePath()).build();
    myGit.getCurrentVersion(vcsRoot);//clone repository

    Browser b = myGit.getBrowserForRoot(vcsRoot);
    Element root = b.getRoot();
    assertDir(root, "", "");

    Map<String, Element> childrenMap = childrenOf(root);
    Element dir = childrenMap.get("dir");
    assertDir(dir, "dir", "dir");

    assertFile(childrenMap.get("readme.txt"), "readme.txt", "readme.txt");

    Map<String, Element> dirChildren = childrenOf(dir);
    assertFile(dirChildren.get("b.txt"), "b.txt", "dir/b.txt");
    assertFile(dirChildren.get("d.txt"), "d.txt", "dir/d.txt");
    assertFile(dirChildren.get("not_ignored_by_checkout_rules.txt"), "not_ignored_by_checkout_rules.txt", "dir/not_ignored_by_checkout_rules.txt");
    assertFile(dirChildren.get("q.txt"), "q.txt", "dir/q.txt");
  }


  public void should_return_empty_browser_if_repository_not_cloned() {
    VcsRoot vcsRoot = vcsRoot().withFetchUrl(myRemoteRepositoryDir.getAbsolutePath()).build();
    Browser b = myGit.getBrowserForRoot(vcsRoot);
    Element root = b.getRoot();
    assertTrue(childrenOf(root).isEmpty());
  }


  private Map<String, Element> childrenOf(@NotNull Element dir) {
    Map<String, Element> childrenMap = new HashMap<String, Element>();
    Iterable<Element> children = dir.getChildren();
    assert children != null;
    for (Element child : children) {
      childrenMap.put(child.getName(), child);
    }
    return childrenMap;
  }

  private void assertFile(@NotNull Element f, @NotNull String name, @NotNull String fullName) {
    assertThat(f.getName(), equalTo(name));
    assertThat(f.getFullName(), equalTo(fullName.replaceAll("/", File.separator)));
    assertTrue(f.isLeaf());
    assertTrue(f.isContentAvailable());
    assertThat(f, hasContentAsInRepository());
  }

  private void assertDir(@NotNull Element dir, @NotNull String name, @NotNull String fullName) throws IOException {
    assertThat(dir.getName(), equalTo(name));
    assertThat(dir.getFullName(), equalTo(fullName.replaceAll("/", File.separator)));
    assertThat(dir, is(directory()));
    assertThat(dir, doesNotSupportInputStream());
    assertThat(dir, doesNotSupportSize());
  }
}
