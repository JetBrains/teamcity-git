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
import jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class FetchMemoryProviderTest {

  @Nullable private Integer myStorage;
  private float myMultFactor;

  @BeforeMethod
  public void setUp() throws IOException {
    myStorage = null;
    myMultFactor = PluginConfigImpl.FETCH_PROCESS_MAX_MEMORY_MULT_FACTOR_DEFAULT;
  }

  @Test
  public void explicit_xmx() throws Throwable {
    then(getValues("20G",null, null, null)).containsExactly(20480).containsExactly(myStorage);
    then(getValues("512M", null, null, null)).containsExactly(512).containsExactly(myStorage);
    then(getValues("1G", null, null, null)).containsExactly(1024).containsExactly(myStorage);
  }

  @Test
  public void increase_disabled() throws Throwable {
    myMultFactor = 1;
    then(getValues(null,null, null, null)).containsExactly(1024).containsExactly(myStorage);
  }

  @Test
  public void free_RAM_all() throws Throwable {
    then(getValues(null, null, null, 3 * 1024)).containsExactly(
      1024, 1433, 2006, 2048
    );
    then(myStorage).isEqualTo(2048);
  }

  @Test
  public void no_cache() throws Throwable {
    then(getValues(null, null, 2048, 8 * 1024)).containsExactly(
      1024, 1433, 2006, 2808
    );
    then(myStorage).isEqualTo(2808);
  }

  @Test
  public void free_RAM_with_cache() throws Throwable {
    myStorage = 512;
    then(getValues(null, null, null, 3 * 1024)).containsExactly(
      512, 716, 1002, 1402, 1962, 2048
    );
    then(myStorage).isEqualTo(2048);
  }

  @Test
  public void with_cache() throws Throwable {
    myStorage = 512;
    then(getValues(null, null, 2048, 8 * 1024)).containsExactly(
      512, 716, 1002, 1402, 1962, 2746
    );
    then(myStorage).isEqualTo(2746);
  }

  @Test
  public void system_limit_all() throws Throwable {
    then(getValues(null, null, null, null)).containsExactly(
      1024, 1433, 2006, 2808, 3931, 4096
    );
    then(myStorage).isEqualTo(4096);
  }

  @Test
  public void system_limit_unreached() throws Throwable {
    then(getValues(null, null, 2048, null)).containsExactly(
      1024, 1433, 2006, 2808
    );
    then(myStorage).isEqualTo(2808);
  }

  @Test
  public void system_limit_with_cache_all() throws Throwable {
    myStorage = 512;
    then(getValues(null, null, null, null)).containsExactly(
      512, 716, 1002, 1402, 1962, 2746, 3844, 4096
    );
    then(myStorage).isEqualTo(4096);
  }

  @Test
  public void system_limit_with_cache() throws Throwable {
    myStorage = 512;
    then(getValues(null, null, 1024, null)).containsExactly(
      512, 716, 1002, 1402
    );
    then(myStorage).isEqualTo(1402);
  }

  @Test
  public void explicit_limit_all() throws Throwable {
    then(getValues(null, "2G", null, 8 * 2014)).containsExactly(
      1024, 1433, 2006, 2048
    );
    then(myStorage).isEqualTo(2048);
  }

  @Test
  public void explicit_limit_unreached() throws Throwable {
    then(getValues(null, "4G", 2048, 8 * 2014)).containsExactly(
      1024, 1433, 2006, 2808
    );
    then(myStorage).isEqualTo(2808);
  }

  @Test
  public void explicit_limit_initial() throws Throwable {
    then(getValues(null, "512M", null, 8 * 2014)).containsExactly(
      512
    );
    then(myStorage).isEqualTo(512);
  }

  @Test
  public void explicit_limit_cache() throws Throwable {
    myStorage = 512;
    then(getValues(null, "1G", null, 8 * 2014)).containsExactly(
      512, 716, 1002, 1024
    );
    then(myStorage).isEqualTo(1024);
  }

  @Test
  public void test_RAM_increase() throws Throwable {
    then(getValues(null, null, null, 3 * 1024)).containsExactly(
      1024, 1433, 2006, 2048
    );
    then(getValues(null, null, null, 4 * 1024)).containsExactly(
      2048, 2867, 3072
    );
  }

  @Test
  public void test_RAM_decrease() throws Throwable {
    then(getValues(null, null, null, 4 * 1024)).containsExactly(
      1024, 1433, 2006, 2808, 3072
    );
    then(getValues(null, null, null, 3 * 1024)).containsExactly(
      2048
    );
  }

  @Test
  public void explicit_xmx_explicit_limit() throws Throwable {
    then(getValues("20G","2G", null, null)).containsExactly(20480).containsExactly(myStorage);
    then(getValues("512M", "2G", null, null)).containsExactly(512).containsExactly(myStorage);
  }

  @DataProvider(name = "test_storage_dp")
  public static Object[][] createData() {
    return new Object[][] {
      new Object[] { 8 * 1024, 4 * 1024 },
      new Object[] { null, 4 * 1024 },
      new Object[] { 4000, 4000 - 1024 }
    };
  }
  @Test(dataProvider = "test_storage_dp")
  public void test_storage(@Nullable Integer freeRam, int maxXmx) {
    {
      final FetchMemoryProvider p1 = createProvider(null, null, freeRam);

      Assert.assertTrue(p1.hasNext());
      then(myStorage).isNull();

      Assert.assertTrue(p1.hasNext());
      then(myStorage).isNull();

      then(p1.next()).isNotNull().isEqualTo(1024).isEqualTo(myStorage);
      Assert.assertTrue(p1.hasNext());
      then(myStorage).isEqualTo(1024);

      then(p1.next()).isNotNull().isEqualTo(1433).isEqualTo(myStorage);
      Assert.assertTrue(p1.hasNext());
      then(myStorage).isEqualTo(1433);
    }

    {
      final FetchMemoryProvider p2 = createProvider(null, null, freeRam);
      Assert.assertTrue(p2.hasNext());
      then(myStorage).isEqualTo(1433);

      then(p2.next()).isNotNull().isEqualTo(1433).isEqualTo(myStorage);
      Assert.assertTrue(p2.hasNext());
      then(myStorage).isEqualTo(1433);

      then(p2.next()).isNotNull().isEqualTo(2006).isEqualTo(myStorage);
      Assert.assertTrue(p2.hasNext());
      then(myStorage).isEqualTo(2006);

      while (p2.hasNext()) p2.next();
      then(myStorage).isEqualTo(maxXmx);
    }

    {
      final FetchMemoryProvider p3 = createProvider(null, null, freeRam);
      Assert.assertTrue(p3.hasNext());
      then(myStorage).isEqualTo(maxXmx);

      then(p3.next()).isNotNull().isEqualTo(maxXmx).isEqualTo(myStorage);
      Assert.assertFalse(p3.hasNext());
      then(myStorage).isEqualTo(maxXmx);
    }
  }

  @NotNull
  private List<Integer> getValues(@Nullable String explicitXmx, @Nullable final String maxXmx, @Nullable final Integer acceptedXmx, @Nullable final Integer freeRAM) throws VcsException {
    final ArrayList<Integer> res = new ArrayList<>();
    final FetchMemoryProvider provider = createProvider(explicitXmx, maxXmx, freeRAM);
    while (provider.hasNext()) {
      final Integer v = provider.next();
      res.add(v);
      if (acceptedXmx != null && v >= acceptedXmx) break;
    }
    return res;
  }

  @NotNull
  private FetchMemoryProvider createProvider(@Nullable final String explicitXmx, @Nullable final String maxXmx, @Nullable final Integer freeRAM) {
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

      @Nullable
      @Override
      public String getMaximumFetchProcessMaxMemory() {
        return maxXmx;
      }

      @NotNull
      @Override
      public String getFetchProcessMaxMemory() {
        return "1024M";
      }

      @Override
      public float getFetchProcessMemoryMultiplyFactor() {
        return myMultFactor;
      }
    }, "test debug info") {
      @Nullable
      @Override
      protected Integer getFreeRAM() {
        return freeRAM;
      }

      @Override
      protected int getTCApprox() {
        return 1024;
      }

      @Override
      protected int getSystemDependentMaxXmx() {
        return 4 * 1024;
      }

      @Override
      protected int getDefaultStartXmx() {
        return 1024;
      }
    };
  }
}
