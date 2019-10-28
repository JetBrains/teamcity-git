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
import jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.MemoryStorage;
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class FetchMemoryProviderTest {

  public static final String URL = "git://some.org/repo.git";
  @NotNull private final Map<String, Long> myMemoryStorage = new HashMap<>();

  @BeforeMethod
  public void setUp() throws IOException {
    myMemoryStorage.clear();
  }

  @Test
  public void explicit_xmx() throws Throwable {
    then(getValues("20G", null, null)).containsExactly(20480L);
    then(getValues("512M", null, null)).containsExactly(512L);
    then(getValues("1G", null, null)).containsExactly(1024L);
  }

  @Test
  public void no_cache_all() throws Throwable {
    then(getValues(null, null, 16 * GitServerUtil.GB)).containsExactly(
      1024L, 1433L, 2006L, 2808L, 3931L, 5503L, 7704L
    );
  }

  @Test
  public void no_cache() throws Throwable {
    then(getValues(null, 4096L, 16 * GitServerUtil.GB)).containsExactly(
      1024L, 1433L, 2006L, 2808L, 3931L, 5503L
    );
  }

  @Test
  public void with_cache() throws Throwable {
    myMemoryStorage.put(URL, 2048L);
    then(getValues(null, 4096L, 16 * GitServerUtil.GB)).containsExactly(
      2048L, 2867L, 4013L, 5618L
    );
  }

  @Test
  public void free_RAM() throws Throwable {
    then(getValues(null, 4096L, 2 * GitServerUtil.GB)).containsExactly(
      1024L
    );
  }

  @NotNull
  private List<Long> getValues(@Nullable String explicitXmx, @Nullable Long acceptedXmx, @Nullable final Long freeRAM) throws VcsException {
    final ArrayList<Long> res = new ArrayList<>();
    createProvider(URL, explicitXmx, freeRAM).withXmx((v, canIncrease) -> {
      res.add(v);
      return acceptedXmx != null && v >= acceptedXmx;
    });
    return res;
  }

  @NotNull
  private FetchMemoryProvider createProvider(@NotNull final String url, @Nullable final String explicitXmx, @Nullable final Long freeRAM) {
    return new FetchMemoryProvider(url, new MemoryStorage() {
      @Nullable
      @Override
      public Long getCachedMemoryValue(@NotNull final String url) {
        return myMemoryStorage.get(url);
      }

      @Override
      public void setCachedMemoryValue(@NotNull final String url, @NotNull final Long value) {
        myMemoryStorage.put(url, value);
      }

      @Override
      public void deleteCachedMemoryValue(@NotNull final String url) {
        myMemoryStorage.remove(url);
      }
    }, new PluginConfigImpl() {
      @Nullable
      @Override
      public String getExplicitFetchProcessMaxMemory() {
        return explicitXmx;
      }
    }) {
      @Nullable
      @Override
      public Long getFreeRAM() {
        return freeRAM;
      }
    };
  }
}
