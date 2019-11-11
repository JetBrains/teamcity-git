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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.MB;

/**
 * Provider of xmx value for separate fetch process.
 *
 * @author vbedrosova
 * @since 2019.2
 */
public class FetchMemoryProvider implements Iterator<Integer> {

  private static Logger LOG = Logger.getInstance(FetchMemoryProvider.class.getName());
  private static double MULTIPLY_FACTOR = 1.4;

  @NotNull private final XmxStorage myStorage;
  @NotNull private final String myDebugInfo;

  @Nullable private final Integer myExplicitXmx;
  @Nullable private final Integer myExplicitMaxXmx;

  @Nullable private Ref<Integer> myNext = null;
  @Nullable private Integer myPrev = null;

  public FetchMemoryProvider(@NotNull final XmxStorage storage,
                             @NotNull final ServerPluginConfig config,
                             @NotNull final String debugInfo) {
    myStorage = storage;
    myDebugInfo = debugInfo;

    myExplicitXmx = getInMB(config.getExplicitFetchProcessMaxMemory());
    myExplicitMaxXmx = getInMB(config.getMaximumFetchProcessMaxMemory());
  }

  public interface XmxStorage {
    /** @return stored xmx value in MB or null if none available */
    @Nullable Integer read();

    /** @param xmx xmx value in MB to be stored for future attempts */
    void write(@Nullable Integer xmx);
  }

  @Override
  public boolean hasNext() {
    if (myNext == null) {
      myNext = new Ref<>(getNext());
    }
    return myNext.get() != null;
  }

  @Override
  public Integer next() {
    if (hasNext() && myNext != null) {
      final Integer next = myNext.get();
      myNext = null;
      return next;
    }
    throw new NoSuchElementException();
  }

  @Nullable
  private Integer getNext() {
    if (isAutoSetupDisabled() && isFirstAttempt()) {
      debug("Automatic git fetch -Xmx setup is disabled. Using explicitly specified " + PluginConfigImpl.TEAMCITY_GIT_FETCH_PROCESS_MAX_MEMORY + " internal property: " + myExplicitXmx + "M");
      return saveAndReturn(myExplicitXmx);

    } else if (isAutoSetupDisabled()) {
      return null;

    } else if (isFirstAttempt()) {
      Integer initial = myStorage.read();
      debug(initial == null
            ? "Using default initial git fetch -Xmx:" + (initial = getDefaultStartXmx()) + "M"
            : "Using previously cached git fetch -Xmx: " + initial + "M");
      return saveAndReturn(applyExplicitLimit(initial));
    }
    else if (wasExplicitLimitReached()) {
      return null;
    }

    final int next = (int)(myPrev * MULTIPLY_FACTOR);

    if (myExplicitMaxXmx  == null) {
      final Integer freeRAM = getFreeRAM();
      if (freeRAM == null) {
        final int maxXmx = getSystemDependentMaxXmx();
        if (next > maxXmx) {
          warn("git fetch -Xmx limit calculated based on the current system maximum: " + maxXmx + "M");
          return saveAndReturnNull(maxXmx);
        }
      } else {
        if (next > freeRAM - getTCUsedApprox()) {
          LOG.warn("Free RAM " + freeRAM + "M is considered not enough to start a new get fetch process with -Xmx: " + next + ". Looks like the system lacks memory. Please contact your system administrator.");
          return saveAndReturnNull(next);
        }
      }
    }
    return saveAndReturn(applyExplicitLimit(next));
  }

  // approximation of how much memory server itself may need
  protected int getTCUsedApprox() {
    return (int) ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()) / MB);
  }

  @NotNull
  private Integer saveAndReturn(@NotNull Integer xmx) {
    myStorage.write(xmx);
    return myPrev = xmx;
  }

  @Nullable
  private Integer saveAndReturnNull(@NotNull Integer xmx) {
    saveAndReturn(xmx);
    return null;
  }

  private boolean isAutoSetupDisabled() {
    return myExplicitXmx != null;
  }

  private boolean isFirstAttempt() {
    return myPrev == null;
  }

  @Nullable
  private static Integer getInMB(@Nullable String val) {
    final Long bytes = GitServerUtil.convertMemorySizeToBytes(val);
    return bytes == null ? null : (int)(bytes / MB);
  }

  // https://www.oracle.com/technetwork/java/hotspotfaq-138619.html#gc_heap_32bit
  protected int getSystemDependentMaxXmx() {
    if (SystemInfo.is32Bit) { // 32 bit Java
      if (SystemInfo.isWindows && System.getenv("ProgramFiles(x86)") == null) {  // 32 bit Windows
        return (int) Math.round(1.4 * 1024);
      }
      return 4 * 1024;
    }
    return 8 * 1024;
  }

  @Nullable
  protected Integer getFreeRAM() {
    final Long freeRamBytes = GitServerUtil.getFreePhysicalMemorySize();
    return freeRamBytes == null ? null : (int) (freeRamBytes / MB);
  }

  protected int getDefaultStartXmx() {
    // we borrow the logic from PluginConfigImpl.getFetchProcessMaxMemory to preserve the default behaviour
    try {
      Class.forName("com.sun.management.OperatingSystemMXBean");
    } catch (ClassNotFoundException e) {
      return 512;
    }
    final Long freeRAM = GitServerUtil.getFreePhysicalMemorySize();
    if (freeRAM != null && freeRAM > GitServerUtil.GB) {
      return 1024;
    } else {
      return 512;
    }
  }

  private int applyExplicitLimit(int xmx) {
    if (myExplicitMaxXmx == null) return xmx;
    if (xmx > myExplicitMaxXmx) {
      info("git fetch -Xmx: " + xmx + "M is limited by the explicitly specified " + PluginConfigImpl.TEAMCITY_GIT_FETCH_PROCESS_MAX_MEMORY_LIMIT + " internal property: " + myExplicitMaxXmx + "M");
      return myExplicitMaxXmx;
    }
    return xmx;
  }

  private boolean wasExplicitLimitReached() {
    if (myExplicitMaxXmx == null || isFirstAttempt()) return false;
    return myExplicitMaxXmx.equals(myPrev);
  }

  private void debug(@NotNull String s) {
    LOG.debug(withInfo(s));
  }

  private void info(@NotNull String s) {
    LOG.warn(withInfo(s));
  }

  private void warn(@NotNull String s) {
    LOG.warn(withInfo(s));
  }

  @NotNull
  private String withInfo(@NotNull final String s) {
    return s + " for " + myDebugInfo;
  }
}
