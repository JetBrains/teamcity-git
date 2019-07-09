/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import jetbrains.buildServer.buildTriggers.vcs.git.MemoryStorageImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.AssertJUnit.*;

@Test
public class MemoryStorageTest extends BaseRemoteRepositoryTest {

  private ServerPluginConfig myConfig;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    ServerPaths serverPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    myConfig = new PluginConfigBuilder(serverPaths).build();
  }

  @Test
  public void createNewMemoryFile() {
    final MemoryStorageImpl storage = new MemoryStorageImpl(myConfig);
    assertNull(storage.getCachedMemoryValue("url"));
    assertTrue(new File(myConfig.getCachesDir(), "memory").exists());
  }

  @Test
  public void saveAndRead() {
    final MemoryStorageImpl storage = new MemoryStorageImpl(myConfig);
    storage.setCachedMemoryValue("url", 123L);

    assertEquals((Long)123L, storage.getCachedMemoryValue("url"));
    assertEquals((Long)123L, new MemoryStorageImpl(myConfig)
      .getCachedMemoryValue("url"));
  }

  @Test
  public void saveAndDelete() {
    final MemoryStorageImpl storage = new MemoryStorageImpl(myConfig);
    storage.setCachedMemoryValue("url", 123L);

    assertEquals((Long)123L, storage.getCachedMemoryValue("url"));
    storage.deleteCachedMemoryValue("url");
    assertNull(storage.getCachedMemoryValue("url"));

    assertNull(new MemoryStorageImpl(myConfig).getCachedMemoryValue("url"));
  }

  @Test
  public void skipWrongLines() throws Exception {
    final File file = new File(myConfig.getCachesDir(), "memory");
    final String content = "good = 3\nbad1 = \nbad2 =  \n bad3= \nbad4=";
    FileUtil.writeFile(file, content, "UTF-8");
    final MemoryStorageImpl storage = new MemoryStorageImpl(myConfig);
    assertEquals((Long)3L, storage.getCachedMemoryValue("good"));
    assertNull(storage.getCachedMemoryValue("bad1"));
    assertNull(storage.getCachedMemoryValue("bad2"));
    assertNull(storage.getCachedMemoryValue("bad3"));
    assertNull(storage.getCachedMemoryValue("bad4"));
  }
}
