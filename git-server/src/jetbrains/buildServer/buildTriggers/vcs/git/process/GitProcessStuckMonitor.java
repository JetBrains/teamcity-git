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

package jetbrains.buildServer.buildTriggers.vcs.git.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.StreamGobbler;
import jetbrains.buildServer.buildTriggers.vcs.git.FetchCommandImpl;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.NamedThreadUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.MB;

/**
 * @author Mikhail Khorkov
 * @author vbedrosova
 */
public abstract class GitProcessStuckMonitor extends Thread {

  private static Logger LOG = Logger.getInstance(FetchCommandImpl.class.getName());

  @NotNull
  private final StreamGobbler myProcGcDump;
  @NotNull
  private final String myCommandLineLog;
  private final long myXmx;

  private final int myCriticalGcDurationSec;
  private final int myCriticalMemoryUsagePercent;
  private final int myCriticalMemoryCleanedPercent;

  private volatile boolean myFinished = false;

  private volatile boolean myInterrupted = false;

  private long lastDumpActivity = 0;

  public GitProcessStuckMonitor(@NotNull final File procGcDump, @NotNull final Long xmx, @NotNull final String commandLineLog) throws VcsException {
    myXmx = xmx;
    try {
      //noinspection ResultOfMethodCallIgnored
      procGcDump.createNewFile();
      myProcGcDump =
        new StreamGobbler(new FileInputStream(procGcDump), null, "GcDump reader for" + commandLineLog);
      myCommandLineLog = commandLineLog;

      myCriticalGcDurationSec = TeamCityProperties.getInteger("teamcity.git.fetch.process.interruption.criticalGcDurationSec", 60 * 5);
      myCriticalMemoryUsagePercent = TeamCityProperties.getInteger("teamcity.git.fetch.process.interruption.criticalMemoryUsagePercent", 100);
      myCriticalMemoryCleanedPercent = TeamCityProperties.getInteger("teamcity.git.fetch.process.interruption.criticalMemoryCleanedPercent", 0);
      setName(NamedThreadUtil.getTcThreadPrefix() + "FetchInterrupter: " + myCommandLineLog);
    } catch (IOException e) {
      throw new VcsException("Fail to create fetch interrupter for " + commandLineLog);
    }
  }

  public void finish() {
    myFinished = true;
  }

  @Override
  public void run() {
    try {
      if (!TeamCityProperties.getBoolean("teamcity.git.fetch.process.interruption.enable")) {
        return;
      }
      myProcGcDump.start();
      while (!myFinished) {
        Thread.sleep(10 * 1000);
        if (myFinished) {
          return;
        }
        if (hasMoreGcDumps() && procIsStuck(parseDump(readDump()))) {
          destroyProc();
          return;
        }
      }
    } catch (Exception e) {
      LOG.warn("Exception while analyzing memory dump for " + myCommandLineLog);
    } finally {
      myProcGcDump.notifyProcessExit();
    }
  }

  @NotNull
  private String readDump() {
    return StringUtil.convertLineSeparators(new String(myProcGcDump.getReadBytes(), StandardCharsets.UTF_8));
  }

  @NotNull
  private List<MemoryDumpLine> parseDump(@NotNull String dump) {
    final List<MemoryDumpLine> result = new ArrayList<>();
    for (final String line : dump.split("\n")) {
      if (StringUtil.isEmpty(line)) {
        continue;
      }
      final String[] raw = line.split(";");
      if (raw.length < 4) {
        continue;
      }
      final List<Long> split =
        Arrays.stream(raw).map(String::trim).map(Long::valueOf).collect(Collectors.toList());
      result.add(new MemoryDumpLine(split.get(0), split.get(1), split.get(2), split.get(3)));
    }
    return result;
  }

  private boolean hasMoreGcDumps() {
    final long oldValue = lastDumpActivity;
    lastDumpActivity = myProcGcDump.getLastActivityTimestamp();
    return oldValue != lastDumpActivity;
  }

  private boolean procIsStuck(@NotNull List<MemoryDumpLine> memoryDumpLines) {
    if (memoryDumpLines.size() < 1) {
      return false;
    }

    final MemoryDumpLine lastLine = memoryDumpLines.get(memoryDumpLines.size() - 1);
    final long cleaned = percent(myXmx, lastLine.getMemoryCleanedMB());
    final long usage = percent(myXmx, lastLine.getMemoryAfterMB());
    final long gcDurationSec = lastLine.getGcDurationSec();

    return cleaned < myCriticalMemoryCleanedPercent
           || usage > myCriticalMemoryUsagePercent
           || gcDurationSec > myCriticalGcDurationSec;
  }

  private int percent(final long all, final long part) {
    return (int) (((double)part / all) * 100);
  }

  private void destroyProc() {
    myInterrupted = true;
    myFinished = true;
    stuckDetected();
  }

  protected abstract void stuckDetected();

  @Override
  public boolean isInterrupted() {
    return myInterrupted;
  }

  private static class MemoryDumpLine {
    private long myTimestamp;
    private long myGcDuration;
    private long myMemoryBefore;
    private long myMemoryAfter;

    MemoryDumpLine(final long timestamp,
                   final long gcDuration,
                   final long memoryBefore,
                   final long memoryAfter) {
      myTimestamp = timestamp;
      myGcDuration = gcDuration;
      myMemoryBefore = memoryBefore;
      myMemoryAfter = memoryAfter;
    }

    long getTimestamp() {
      return myTimestamp;
    }

    long getGcDuration() {
      return myGcDuration;
    }

    long getGcDurationSec() {
      return myGcDuration / 1000;
    }

    long getMemoryBefore() {
      return myMemoryBefore;
    }

    long getMemoryAfter() {
      return myMemoryAfter;
    }

    long getMemoryAfterMB() {
      return myMemoryAfter / MB;
    }

    long getMemoryCleanedMB() {
      return (myMemoryBefore - myMemoryAfter) / MB;
    }
  }
}