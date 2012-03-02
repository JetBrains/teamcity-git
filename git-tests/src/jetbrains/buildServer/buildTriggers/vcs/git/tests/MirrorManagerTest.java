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
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.getRepository;
import static org.testng.AssertJUnit.*;

/**
 * @author dmitry.neverov
 */
@Test
public class MirrorManagerTest {

  private TempFiles myTempFiles = new TempFiles();
  private ServerPluginConfig myConfig;


  @BeforeMethod
  public void setUp() throws Exception {
    ServerPaths paths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    myConfig = new PluginConfigBuilder(paths).build();
  }


  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }


  public void should_handle_clashing_urls() {
    String clashingUrl1 = "git://some.org/first-clashing-url.git";
    String clashingUrl2 = "git://some.org/second-clashing-url.git";
    HashCalculator clashingHash = new ClashingHashCalculator(Arrays.asList(clashingUrl1, clashingUrl2));
    MirrorManager mirrorManager = new MirrorManagerImpl(myConfig, clashingHash);
    File dir1 = mirrorManager.getMirrorDir(clashingUrl1);
    File dir2 = mirrorManager.getMirrorDir(clashingUrl2);
    assertFalse(dir1.equals(dir2));
  }


  public void should_restore_mapping_from_existing_repositories() throws Exception {
    //There are 3 existing repositories and no map file (this is the first start after
    //mirror manager is introduced). After the start map file should be created
    //and mirrorManager should respect old repositories locations.

    File baseMirrorsDir = myConfig.getCachesDir();
    Map<String, String> existingRepositories = new HashMap<String, String>() {{
      put("git://some.org/repository1.git", "git-11111111.git");
      put("git://some.org/repository2.git", "git-22222222.git");
      put("git://some.org/repository3.git", "git-33333333.git");
    }};

    File map = new File(baseMirrorsDir, "map");
    createRepositories(baseMirrorsDir, existingRepositories);
    assertFalse(map.exists());

    MirrorManager mirrorManager = new MirrorManagerImpl(myConfig, new HashCalculatorImpl());
    assertTrue(map.exists());
    for (Map.Entry<String, String> entry : existingRepositories.entrySet()) {
      String url = entry.getKey();
      String dir = entry.getValue();
      assertEquals(new File(baseMirrorsDir, dir), mirrorManager.getMirrorDir(url));
    }
  }


  public void should_give_different_dirs_for_same_url_if_dir_was_invalidated() {
    MirrorManager mirrorManager = new MirrorManagerImpl(myConfig, new HashCalculatorImpl());
    String url = "git://some.org/repository.git";
    File dir1 = mirrorManager.getMirrorDir(url);
    mirrorManager.invalidate(dir1);
    File dir2 = mirrorManager.getMirrorDir(url);
    assertFalse(dir1.equals(dir2));
  }


  public void should_remember_invalidated_dirs_after_restart() {
    String url1 = "git://some.org/repository1.git";
    String url2 = "git://some.org/repository2.git";
    HashCalculator clashingHash = new ClashingHashCalculator(Arrays.asList(url1, url2));
    MirrorManager mirrorManager = new MirrorManagerImpl(myConfig, clashingHash);
    File dir1 = mirrorManager.getMirrorDir(url1);
    mirrorManager.invalidate(dir1);
    mirrorManager = new MirrorManagerImpl(myConfig, clashingHash); //restart
    File dir2 = mirrorManager.getMirrorDir(url2);
    assertFalse(dir1.equals(dir2));
  }


  private void createRepositories(File baseDir, Map<String, String> url2dir) throws Exception {
    for (Map.Entry<String, String> entry : url2dir.entrySet()) {
      String url = entry.getKey();
      String dir = entry.getValue();
      getRepository(new File(baseDir, dir), new URIish(url));
    }
  }


  private static class ClashingHashCalculator implements HashCalculator {
    private final List<String> myValuesWhenShouldClash;

    ClashingHashCalculator(List<String> valuesWhenShouldClash) {
      myValuesWhenShouldClash = valuesWhenShouldClash;
    }

    public long getHash(@NotNull String value) {
      if (myValuesWhenShouldClash.contains(value))
        return 42;
      else
        return value.hashCode();
    }
  }
}
