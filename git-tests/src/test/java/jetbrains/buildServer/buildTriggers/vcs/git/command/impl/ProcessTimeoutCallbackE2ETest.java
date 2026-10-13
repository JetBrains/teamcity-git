package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.processes.ProcessPid;
import com.intellij.openapi.util.SystemInfo;
import org.testng.SkipException;
import org.testng.annotations.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ProcessTimeoutCallbackE2ETest {
  private static final int GRACE_TIMEOUT_SECONDS = 1;
  private static final long MIN_EXPECTED_GRACE_MILLIS = 900;
  private static final long MAX_SHARED_DEADLINE_MILLIS = 2500;

  @Test
  public void terminatesMultipleStubbornProcessTreesWithinOneSharedDeadline() throws Exception {
    if (!SystemInfo.isUnix) throw new SkipException("Unix signals are required");
    try {
      Class.forName("java.lang.ProcessHandle");
    } catch (ClassNotFoundException e) {
      throw new SkipException("ProcessHandle requires Java 9 or later");
    }
    final String previousEnabledSetting = System.getProperty(ProcessTimeoutCallback.GRACEFUL_TERMINATION_ENABLED);
    final String previousTimeoutSetting = System.getProperty(ProcessTimeoutCallback.GRACEFUL_TERMINATION_TIMEOUT_SECONDS);
    final String previousPidResolverSetting = System.getProperty(ProcessPid.ENABLED_PROP);
    final List<StubbornProcessTree> processTrees = new ArrayList<>();

    try {
      System.setProperty(ProcessTimeoutCallback.GRACEFUL_TERMINATION_ENABLED, "true");
      System.setProperty(ProcessTimeoutCallback.GRACEFUL_TERMINATION_TIMEOUT_SECONDS,
                         Integer.toString(GRACE_TIMEOUT_SECONDS));
      System.setProperty(ProcessPid.ENABLED_PROP, "false");

      processTrees.add(StubbornProcessTree.start());
      processTrees.add(StubbornProcessTree.start());
      assertNull(ProcessPid.getPid(processTrees.get(0).rootProcess, "E2E root process"));

      for (StubbornProcessTree processTree : processTrees) {
        new ProcessTimeoutCallback(60, null).onProcessStarted(processTree.rootProcess);
      }

      final long startedNanos = System.nanoTime();
      assertEquals(processTrees.size(), ProcessTimeoutCallback.terminateRunningProcesses());
      final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);

      assertTrue(elapsedMillis >= MIN_EXPECTED_GRACE_MILLIS,
                 "stubborn processes should survive until the grace deadline; elapsed=" + elapsedMillis + "ms");
      assertTrue(elapsedMillis < MAX_SHARED_DEADLINE_MILLIS,
                 "process-tree timeouts were serialized; elapsed=" + elapsedMillis + "ms");
      for (StubbornProcessTree processTree : processTrees) {
        processTree.assertTerminated();
      }
    } finally {
      restoreProperty(ProcessTimeoutCallback.GRACEFUL_TERMINATION_ENABLED, previousEnabledSetting);
      restoreProperty(ProcessTimeoutCallback.GRACEFUL_TERMINATION_TIMEOUT_SECONDS, previousTimeoutSetting);
      restoreProperty(ProcessPid.ENABLED_PROP, previousPidResolverSetting);
      for (StubbornProcessTree processTree : processTrees) {
        processTree.forceCleanup();
      }
    }
  }

  private static void restoreProperty(String name, String value) {
    if (value == null) {
      System.clearProperty(name);
    } else {
      System.setProperty(name, value);
    }
  }

  private static final class StubbornProcessTree {
    private final Process rootProcess;
    private final Object childProcess;

    private StubbornProcessTree(Process rootProcess, Object childProcess) {
      this.rootProcess = rootProcess;
      this.childProcess = childProcess;
    }

    private static StubbornProcessTree start() throws Exception {
      final Process rootProcess = new ProcessBuilder("sh",
                                                     "-c",
                                                     "trap '' TERM; sleep 30 & echo $!; wait").start();
      final long childPid = Long.parseLong(
        new BufferedReader(new InputStreamReader(rootProcess.getInputStream())).readLine());
      final Object childProcess = ((Optional<?>) Class.forName("java.lang.ProcessHandle").getMethod("of", long.class).invoke(null, childPid))
        .orElseThrow(() -> new AssertionError("child process was not started"));
      return new StubbornProcessTree(rootProcess, childProcess);
    }

    private void assertTerminated() throws Exception {
      assertTrue(rootProcess.waitFor(2, TimeUnit.SECONDS), "root process survived forced termination");
      assertTrue(waitForExit(childProcess), "child process survived forced termination");
    }

    private void forceCleanup() throws Exception {
      if (isAlive(childProcess)) {
        Class.forName("java.lang.ProcessHandle").getMethod("destroyForcibly").invoke(childProcess);
      }
      if (rootProcess.isAlive()) {
        rootProcess.destroyForcibly();
      }
    }

    private static boolean waitForExit(Object processHandle) throws Exception {
      for (int attempt = 0; attempt < 40 && isAlive(processHandle); attempt++) {
        Thread.sleep(50);
      }
      return !isAlive(processHandle);
    }

    private static boolean isAlive(Object processHandle) throws Exception {
      return Boolean.TRUE.equals(Class.forName("java.lang.ProcessHandle").getMethod("isAlive").invoke(processHandle));
    }
  }
}
