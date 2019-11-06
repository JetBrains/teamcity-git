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

  private final XmxStorage myStorage;
  private final ServerPluginConfig myConfig;

  public FetchMemoryProvider(final XmxStorage storage,
                             final ServerPluginConfig config) {
    myStorage = storage;
    myConfig = config;
  }

  public interface XmxConsumer {
    /**
     * @param xmx value in MB
     * @param canIncrease flag showing if this attempt is final or xmx can be increased more
     * @return true if attempt was successful
     * @throws VcsException in case of attempt failure
     */
    boolean withXmx(@NotNull Integer xmx, boolean canIncrease) throws VcsException;
  }

  public interface XmxStorage {
    /** @return stored xmx value in MB or null if none available */
    @Nullable Integer read();

    /** @param xmx xmx value in MB to be stored for future attempts */
    void write(@Nullable Integer xmx);
  }

  public void withXmx(@NotNull XmxConsumer consumer) throws VcsException {
    final Long explicitXmx = getExplicitXmxMB();
    if (explicitXmx != null) {
      myStorage.write(null);
      consumer.withXmx(explicitXmx.intValue(), false);
      return;
    }

    final Long[] values = getMemoryValues().stream().toArray(Long[]::new);
    for (int i = 0; i < values.length; ++i) {
      final Integer xmx = values[i].intValue();
      myStorage.write(xmx);
      if (consumer.withXmx(xmx, i < values.length - 1)) break;
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
    final Integer cachedXmx = myStorage.read();
    if (cachedXmx == null) {
      xmx = getExplicitOrDefaultXmxMB();
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

  private long getMaxXmxMB() {
    long maxXmx = getSystemDependentMaxXmx();
    final Long freeRAM = getFreeRAM();
    if (freeRAM == null) return 512L;
    if (freeRAM < maxXmx) {
      do {
        maxXmx /= MULTIPLE_FACTOR;
      } while (maxXmx > freeRAM);
    }
    return maxXmx / GitServerUtil.MB;
  }

  // https://www.oracle.com/technetwork/java/hotspotfaq-138619.html#gc_heap_32bit
  public long getSystemDependentMaxXmx() {
    if (SystemInfo.is32Bit) { // 32 bit Java
      if (SystemInfo.isWindows && System.getenv("ProgramFiles(x86)") == null) {  // 32 bit Windows
        return 1433 * GitServerUtil.MB; // ~1.4G
      }
      return 4 * GitServerUtil.GB;
    }
    return 8 * GitServerUtil.GB;
  }

  @Nullable
  public Long getFreeRAM() {
    return GitServerUtil.getFreePhysicalMemorySize();
  }

  private long getExplicitOrDefaultXmxMB() {
    final String explicitOrDefault = myConfig.getFetchProcessMaxMemory();
    final Long parsed = GitServerUtil.convertMemorySizeToBytes(explicitOrDefault);
    if (parsed == null) {
      LOG.warn("Cannot parse memory value '" + explicitOrDefault + "'");
      return 512L;
    }
    return parsed / GitServerUtil.MB;
  }
}
