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

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Provider of xmx value for separate fetch process.
 *
 * @author vbedrosova
 * @since 2019.2
 */
public class FetchMemoryProvider {

  private static Logger LOG = Logger.getInstance(FetchMemoryProvider.class.getName());
  private static double MULTIPLE_FACTOR = 1.4;

  private final String myUrl;
  private final MemoryStorage myMemoryStorage;
  private final ServerPluginConfig myConfig;

  public FetchMemoryProvider(final String url,
                             final MemoryStorage memoryStorage,
                             final ServerPluginConfig config) {
    myUrl = url;
    myMemoryStorage = memoryStorage;
    myConfig = config;
  }

  public interface XmxConsumer {
    boolean withXmx(@NotNull Long xmx, boolean canIncrease) throws VcsException;
  }

  public void withXmx(@NotNull XmxConsumer consumer) throws VcsException {
    final Long[] values = getMemoryValues().stream().toArray(Long[]::new);
    for (int i = 0; i < values.length; ++i) {
      final Long xmx = values[i];
      if (consumer.withXmx(xmx, i == values.length - 1)) {
        myMemoryStorage.setCachedMemoryValue(myUrl, xmx);
        return;
      }
      myMemoryStorage.deleteCachedMemoryValue(myUrl);
    }
  }

  @Nullable
  private Long getExplicitXmxMB() {
    final Long xmx = GitServerUtil.convertMemorySizeToBytes(myConfig.getExplicitFetchProcessMaxMemory());
    return xmx == null ? null : xmx / GitServerUtil.MB;
  }

  @NotNull
  private List<Long> getMemoryValues() {
    final Long explicitXmx = getExplicitXmxMB();
    if (explicitXmx != null) {
      return Collections.singletonList(explicitXmx);
    }

    long xmx;
    final Long cachedXmx = myMemoryStorage.getCachedMemoryValue(myUrl);
    if (cachedXmx == null) {
      xmx = userPreferenceOrDefaultMB();
    } else {
      xmx = cachedXmx;
    }

    final ArrayList<Long> values = new ArrayList<>();

    final long maxXmx = getMaxXmxMB();
    while (xmx <= maxXmx) {
      values.add(xmx);
      xmx *= MULTIPLE_FACTOR;
    }
    if (values.isEmpty()) {
      // anyway try last value
      values.add(xmx);
    }
    return values;
  }

  // https://www.oracle.com/technetwork/java/hotspotfaq-138619.html#gc_heap_32bit
  @NotNull
  private Long getMaxXmxMB() {
    long res;
    if (SystemInfo.is32Bit) { //32bit Java
      res = SystemInfo.isWindows
            ? 1433 * GitServerUtil.MB // ~1.4G
            : 4 * GitServerUtil.GB;
    } else {
      res = 8 * GitServerUtil.GB;
    }
    final Long freeRAM = getFreeRAM();
    return freeRAM == null || freeRAM > res ? res / GitServerUtil.MB : userPreferenceOrDefaultMB();
  }

  @Nullable
  public Long getFreeRAM() {
    return GitServerUtil.getFreePhysicalMemorySize();
  }

  @NotNull
  private Long userPreferenceOrDefaultMB() {
    final String preferenceWithM = myConfig.getFetchProcessMaxMemory();
    final Long parsed = GitServerUtil.convertMemorySizeToBytes(preferenceWithM);
    if (parsed == null) {
      LOG.warn("Cannot parse memory value '" + preferenceWithM + "'");
      return 512L;
    }
    return parsed / GitServerUtil.MB;
  }
}
