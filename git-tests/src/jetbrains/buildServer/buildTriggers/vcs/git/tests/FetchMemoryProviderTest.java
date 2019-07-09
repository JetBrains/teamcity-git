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

import jetbrains.buildServer.buildTriggers.vcs.git.FetchMemoryProvider;
import jetbrains.buildServer.buildTriggers.vcs.git.MemoryStorageImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

@Test
public class FetchMemoryProviderTest extends BaseRemoteRepositoryTest {

  private ServerPluginConfig myConfig;
  private FetchMemoryProvider.FreeRAMProvider myRAMProvider = new FreeRAMProviderMock(1000L);

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    System.setProperty("teamcity.git.fetch.process.max.memory", "512M");
    ServerPaths serverPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    myConfig = new PluginConfigBuilder(serverPaths).build();
  }

  @Test
  public void getDefaultValue() throws Exception {
    final MemoryStorageImpl storage = new MemoryStorageImpl(myConfig);
    final FetchMemoryProvider provider = new FetchMemoryProvider("url", storage, myConfig);
    provider.setFreeRAMProvider(myRAMProvider);
    assertEquals(512L, provider.getNextTryMemoryAmount());
    assertEquals((long)(512 * 1.4), provider.getNextTryMemoryAmount());
  }

  @Test
  public void readPreviousValue() throws Exception {
    writeStorage("url", "123", "foo", "3");
    final MemoryStorageImpl storage = new MemoryStorageImpl(myConfig);
    final FetchMemoryProvider provider = new FetchMemoryProvider("url", storage, myConfig);
    provider.setFreeRAMProvider(myRAMProvider);
    assertEquals(123L, provider.getNextTryMemoryAmount());
    assertEquals((long)(123 * 1.4), provider.getNextTryMemoryAmount());
  }

  @Test
  public void sameAsPrevious() throws Exception {
    writeStorage("url", "1");
    final MemoryStorageImpl storage = new MemoryStorageImpl(myConfig);
    final FetchMemoryProvider provider = new FetchMemoryProvider("url", storage, myConfig);
    provider.setFreeRAMProvider(myRAMProvider);
    assertEquals(1, provider.getNextTryMemoryAmount());
    /* 1 * 1.4 == 1 */
    assertEquals(-1, provider.getNextTryMemoryAmount());
    /* value deleted in we get -1 */
    assertNull(storage.getCachedMemoryValue("url"));
    final FetchMemoryProvider freshProvider = new FetchMemoryProvider("url", storage, myConfig);
    freshProvider.setFreeRAMProvider(myRAMProvider);
    assertEquals(512L, freshProvider.getNextTryMemoryAmount());
  }

  @Test
  public void lessThenMemory() throws Exception {
    writeStorage("url", "1001");
    final MemoryStorageImpl storage = new MemoryStorageImpl(myConfig);
    final FetchMemoryProvider provider = new FetchMemoryProvider("url", storage, myConfig);
    provider.setFreeRAMProvider(myRAMProvider);
    assertEquals(1000, provider.getNextTryMemoryAmount());
    assertEquals(-1, provider.getNextTryMemoryAmount());
    /* value deleted in we get -1 */
    assertNull(storage.getCachedMemoryValue("url"));
    final FetchMemoryProvider freshProvider = new FetchMemoryProvider("url", storage, myConfig);
    freshProvider.setFreeRAMProvider(myRAMProvider);
    assertEquals(512L, freshProvider.getNextTryMemoryAmount());
  }

  private void writeStorage(String... values) throws Exception {
    final StringBuilder content = new StringBuilder();
    for (int i = 0; i < values.length / 2; i++) {
      content.append(values[i * 2]).append(" = ").append(Long.parseLong(values[i * 2 + 1])).append("\n");
    }
    FileUtil.writeFile(new File(myConfig.getCachesDir(), "memory"), content.toString(), "UTF-8");
  }

  private static class FreeRAMProviderMock implements FetchMemoryProvider.FreeRAMProvider {

    private Long myRam;

    public FreeRAMProviderMock(final Long ram) {
      myRam = ram;
    }

    public void setRam(final Long ram) {
      myRam = ram;
    }

    @Nullable
    @Override
    public Long freeRAMInMB() {
      return myRam;
    }
  }
}
