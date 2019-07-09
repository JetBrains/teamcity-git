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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Provider of xmx value for separate fetch process.
 *
 * @author Mikhail Khorkov
 * @since 2019.2
 */
public class FetchMemoryProvider {

  private static Logger LOG = Logger.getInstance(FetchMemoryProvider.class.getName());
  private static double MULTIPLE_FACTOR = 1.4;
  private static long MAX_MEMORY_VALUE_MB = 10 * 1024 * 1024; // 10GB

  private final String myUrl;
  private final MemoryStorage myMemoryStorage;
  private final ServerPluginConfig myConfig;
  private FreeRAMProvider myFreeRAMProvider = new FreeRAMProviderImpl();

  private long myLastValue = 0;

  public FetchMemoryProvider(final String url,
                             final MemoryStorage memoryStorage,
                             final ServerPluginConfig config) {
    myUrl = url;
    myMemoryStorage = memoryStorage;
    myConfig = config;
  }

  /**
   * Returns amount of memory to set xmx flag for separate fetch process.
   *
   * Each call of the method will return more value then previous one.
   * If the new value equals or less then a previous one then -1 will be returned.
   * If the new value more then 10GB then -1 will be returned.
   */
  public long getNextTryMemoryAmount() {
    final long previous = myLastValue;

    if (myLastValue == -1) {
      return myLastValue;
    } else if (myLastValue == 0) {
      Long value = myMemoryStorage.getCachedMemoryValue(myUrl);
      if (value == null) {
        value = userPreferenceInMB();
      }
      myLastValue = value;
    } else {
      myLastValue = (long)(myLastValue * MULTIPLE_FACTOR);
    }

    final Long freeRAM = myFreeRAMProvider.freeRAMInMB();
    if (freeRAM != null && myLastValue > freeRAM) {
      myLastValue = freeRAM;
    }

    if (previous == myLastValue || previous > myLastValue || myLastValue > MAX_MEMORY_VALUE_MB) {
      myLastValue = -1;
      myMemoryStorage.deleteCachedMemoryValue(myUrl);
    } else {
      myMemoryStorage.setCachedMemoryValue(myUrl, myLastValue);
    }

    return myLastValue;
  }

  @NotNull
  private Long userPreferenceInMB() {
    final String preference = myConfig.getExplicitFetchProcessMaxMemory();
    final Long parsed = GitServerUtil.convertMemorySizeToBytes(preference);
    if (parsed == null) {
      LOG.warn("Cannot parse memory value '" + preference + "'");
      return 512L;
    }
    return parsed / (1024 * 1024);
  }

  public void setFreeRAMProvider(@NotNull final FreeRAMProvider freeRAMProvider) {
    myFreeRAMProvider = freeRAMProvider;
  }

  public interface FreeRAMProvider {
    /**
     * Return free memory space in MB or <code>null</code>.
     */
    @Nullable
    Long freeRAMInMB();
  }

  public static class FreeRAMProviderImpl implements FreeRAMProvider {

    /**
     * Return free memory space in MB or <code>null</code>.
     */
    @Nullable
    @Override
    public Long freeRAMInMB() {
      return Optional.ofNullable(GitServerUtil.getFreePhysicalMemorySize())
        .map(f -> f / 1024 / 1024).orElse(null);
    }
  }
}
