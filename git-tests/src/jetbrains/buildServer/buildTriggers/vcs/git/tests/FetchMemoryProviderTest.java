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
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class FetchMemoryProviderTest {

  @Nullable private Integer myStorage;

  @BeforeMethod
  public void setUp() throws IOException {
    myStorage = null;
  }

  @Test
  public void explicit_xmx() throws Throwable {
    then(getValues("20G", null, null)).containsExactly(20480);
    then(getValues("512M", null, null)).containsExactly(512);
    then(getValues("1G", null, null)).containsExactly(1024);
    then(myStorage).isNull();
  }

  @Test
  public void no_cache_all() throws Throwable {
    then(getValues(null, null, 16 * GitServerUtil.GB)).containsExactly(
      1024, 1433, 2006, 2808, 3931
    );
    then(myStorage).isEqualTo(3931);
  }

  @Test
  public void no_cache() throws Throwable {
    then(getValues(null, 2048, 16 * GitServerUtil.GB)).containsExactly(
      1024, 1433, 2006, 2808
    );
    then(myStorage).isEqualTo(2808);
  }

  @Test
  public void with_cache_all() throws Throwable {
    myStorage = 512;
    then(getValues(null, null, 16 * GitServerUtil.GB)).containsExactly(
      512, 716, 1002, 1402, 1962, 2746, 3844
    );
    then(myStorage).isEqualTo(3844);
  }

  @Test
  public void with_cache() throws Throwable {
    myStorage = 512;
    then(getValues(null, 2048, 16 * GitServerUtil.GB)).containsExactly(
      512, 716, 1002, 1402, 1962, 2746
    );
    then(myStorage).isEqualTo(2746);
  }

  @Test
  public void free_RAM_all() throws Throwable {
    then(getValues(null, 4096, 2 * GitServerUtil.GB)).containsExactly(
      1024, 1433
    );
    then(myStorage).isEqualTo(1433);
  }

  @Test
  public void free_RAM() throws Throwable {
    then(getValues(null, 1024, 2 * GitServerUtil.GB)).containsExactly(
      1024
    );
    then(myStorage).isEqualTo(1024);
  }

  @NotNull
  private List<Integer> getValues(@Nullable String explicitXmx, @Nullable final Integer acceptedXmx, @Nullable final Long freeRAM) throws VcsException {
    final ArrayList<Integer> res = new ArrayList<>();
    createProvider(explicitXmx, freeRAM).withXmx((v, canIncrease) -> {
      res.add(v);
      return acceptedXmx != null && v >= acceptedXmx;
    });
    return res;
  }

  @NotNull
  private FetchMemoryProvider createProvider(@Nullable final String explicitXmx, @Nullable final Long freeRAM) {
    return new FetchMemoryProvider(new FetchMemoryProvider.XmxStorage() {
      @Nullable
      @Override
      public Integer read() {
        return myStorage;
      }

      @Override
      public void write(@Nullable final Integer xmx) {
        myStorage = xmx;
      }
    }, new PluginConfigImpl() {
      @Nullable
      @Override
      public String getExplicitFetchProcessMaxMemory() {
        return explicitXmx;
      }

      @NotNull
      @Override
      public String getFetchProcessMaxMemory() {
        return "1024M";
      }
    }) {
      @Nullable
      @Override
      public Long getFreeRAM() {
        return freeRAM;
      }

      @Override
      public long getSystemDependentMaxXmx() {
        return 4 * GitServerUtil.GB;
      }
    };
  }
}
