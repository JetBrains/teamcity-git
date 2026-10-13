package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.processes.ProcessPid;
import com.intellij.openapi.util.SystemInfo;
import org.testng.SkipException;
import org.testng.annotations.Test;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ProcessTimeoutCallbackTest {

  @Test
  public void requestsGracefulProcessTreeTerminationBeforeBuildInterruption() {
    StubProcess process = new StubProcess(true);
    RecordingProcessTreeKiller processTreeKiller = new RecordingProcessTreeKiller(true);
    ProcessTimeoutCallback callback = callback(true, 7, processTreeKiller, 123L);

    callback.onProcessStarted(process);
    callback.terminateProcessTreeIfRunning();

    assertEquals(Long.valueOf(123L), processTreeKiller.pid);
    assertEquals(1, processTreeKiller.invocationCount);
    assertEquals(0, processTreeKiller.forceKillCount);
  }

  @Test
  public void resolvesPidFromRuntimeWhenTeamCityResolverIsUnavailable() throws Exception {
    if (!SystemInfo.isUnix) throw new SkipException("Unix shell is required");
    try {
      Process.class.getMethod("pid");
    } catch (NoSuchMethodException e) {
      throw new SkipException("Process.pid() requires Java 9 or later");
    }
    Process process = new ProcessBuilder("sh", "-c", "echo $$; exec sleep 30").start();
    String previousResolverSetting = System.getProperty(ProcessPid.ENABLED_PROP);
    try {
      Long expectedPid = Long.valueOf(new BufferedReader(new InputStreamReader(process.getInputStream())).readLine());
      System.setProperty(ProcessPid.ENABLED_PROP, "false");

      assertNull(ProcessPid.getPid(process, "test process"));
      assertEquals(expectedPid, ProcessTimeoutCallback.resolveProcessPid(process));
    } finally {
      if (previousResolverSetting == null) {
        System.clearProperty(ProcessPid.ENABLED_PROP);
      } else {
        System.setProperty(ProcessPid.ENABLED_PROP, previousResolverSetting);
      }
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void preservesTeamCityForceTreeTerminationForOutputIdleTimeouts() {
    RecordingProcessTreeKiller processTreeKiller = new RecordingProcessTreeKiller();
    ProcessTimeoutCallback callback = callback(true, 5, processTreeKiller, 123L);

    assertTrue(callback.terminateEntireProcessTree());
  }

  @Test
  public void leavesNormallyFinishedProcessAlone() {
    StubProcess process = new StubProcess(true);
    RecordingProcessTreeKiller processTreeKiller = new RecordingProcessTreeKiller();
    ProcessTimeoutCallback callback = callback(true, 5, processTreeKiller, 123L);

    callback.onProcessStarted(process);
    callback.onProcessFinished(process);
    callback.terminateProcessTreeIfRunning();

    assertNull(processTreeKiller.pid);
    assertFalse(process.forciblyDestroyed);
  }

  @Test
  public void preservesTeamCityForceTreeTerminationWhenGracefulTerminationIsDisabled() {
    StubProcess process = new StubProcess(true);
    RecordingProcessTreeKiller processTreeKiller = new RecordingProcessTreeKiller();
    ProcessTimeoutCallback callback = callback(false, 5, processTreeKiller, 123L);

    callback.onProcessStarted(process);
    callback.terminateProcessTreeIfRunning();

    assertTrue(callback.terminateEntireProcessTree());
    assertNull(processTreeKiller.pid);
  }

  @Test
  public void gracefullyTerminatesTreeWhenPidCannotBeResolved() {
    StubProcess process = new StubProcess(true);
    RecordingProcessTreeKiller processTreeKiller = new RecordingProcessTreeKiller(true);
    ProcessTimeoutCallback callback = callback(true, 3, processTreeKiller, null);

    callback.onProcessStarted(process);
    callback.terminateProcessTreeIfRunning();

    assertNull(processTreeKiller.pid);
    assertEquals(1, processTreeKiller.invocationCount);
    assertEquals(0, processTreeKiller.forceKillCount);
  }

  @Test
  public void forceKillsSurvivorsAtTheSingleDeadline() {
    StubProcess process = new StubProcess(true);
    RecordingProcessTreeKiller processTreeKiller = new RecordingProcessTreeKiller(false);
    ProcessTimeoutCallback callback = callback(true, 0, processTreeKiller, 123L);

    callback.onProcessStarted(process);
    callback.terminateProcessTreeIfRunning();

    assertTrue(process.forciblyDestroyed);
    assertEquals(1, processTreeKiller.forceKillCount);
  }

  @Test
  public void forceKillsRootWhenProcessTreeTerminationCannotStart() {
    StubProcess process = new StubProcess(true);
    ProcessTimeoutCallback callback = callback(true,
                                                0,
                                                (ignoredProcess, ignoredPid) -> {
                                                  throw new IllegalStateException("unavailable");
                                                },
                                                123L);

    callback.onProcessStarted(process);
    callback.terminateProcessTreeIfRunning();

    assertTrue(process.forciblyDestroyed);
  }

  @Test
  public void startsEveryRegisteredTreeBeforeTheSharedDeadline() {
    List<String> events = Collections.synchronizedList(new ArrayList<>());
    StubProcess firstProcess = new StubProcess(true);
    StubProcess secondProcess = new StubProcess(true);
    RecordingProcessTreeKiller firstKiller = new RecordingProcessTreeKiller(false, "first", events);
    RecordingProcessTreeKiller secondKiller = new RecordingProcessTreeKiller(false, "second", events);
    ProcessTimeoutCallback firstCallback = callback(true, 60, firstKiller, 123L);
    ProcessTimeoutCallback secondCallback = callback(true, 60, secondKiller, 456L);

    firstCallback.onProcessStarted(firstProcess);
    secondCallback.onProcessStarted(secondProcess);

    assertEquals(2, ProcessTimeoutCallback.terminateRunningProcesses(0));
    int firstForce = Math.min(events.indexOf("force-first"), events.indexOf("force-second"));
    assertTrue(events.indexOf("start-first") < firstForce);
    assertTrue(events.indexOf("start-second") < firstForce);
    assertTrue(firstProcess.forciblyDestroyed);
    assertTrue(secondProcess.forciblyDestroyed);
  }

  @Test
  public void startsProcessTreeTerminationConcurrently() {
    assertProcessTreeOperationsRunConcurrently(true);
  }

  @Test
  public void forceKillsProcessTreesConcurrently() {
    assertProcessTreeOperationsRunConcurrently(false);
  }

  @Test
  public void completesCleanupAndRestoresInterruptWhenInterruptedDuringTreeDiscovery() throws Exception {
    StubProcess process = new StubProcess(true);
    CountDownLatch discoveryStarted = new CountDownLatch(1);
    CountDownLatch allowDiscoveryToComplete = new CountDownLatch(1);
    AtomicBoolean discoveryCompleted = new AtomicBoolean();
    AtomicBoolean cleanupCompletedBeforeReturn = new AtomicBoolean();
    AtomicBoolean interruptRestored = new AtomicBoolean();
    AtomicInteger terminationCount = new AtomicInteger();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    ProcessTimeoutCallback callback = callback(true, 60, (ignoredProcess, ignoredPid) -> {
      discoveryStarted.countDown();
      try {
        assertTrue(allowDiscoveryToComplete.await(5, TimeUnit.SECONDS), "tree discovery was never released");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("tree discovery worker was interrupted before cleanup", e);
      }
      discoveryCompleted.set(true);
      return new ProcessTimeoutCallback.ProcessTreeTermination() {
        @Override
        public boolean isAlive() {
          return process.isAlive();
        }

        @Override
        public void forceKill() {
          process.destroyForcibly();
        }
      };
    }, 123L);
    callback.onProcessStarted(process);
    Thread cancellation = new Thread(() -> {
      try {
        terminationCount.set(ProcessTimeoutCallback.terminateRunningProcesses(60));
        cleanupCompletedBeforeReturn.set(discoveryCompleted.get() && process.forciblyDestroyed);
        interruptRestored.set(Thread.currentThread().isInterrupted());
      } catch (Throwable e) {
        failure.set(e);
      }
    }, "git-termination-interruption-test");
    cancellation.setDaemon(true);

    try {
      cancellation.start();
      assertTrue(discoveryStarted.await(5, TimeUnit.SECONDS), "tree discovery did not start");
      cancellation.interrupt();
      allowDiscoveryToComplete.countDown();
      cancellation.join(5000);

      assertFalse(cancellation.isAlive(), "interrupted cancellation waited for another grace period");
      assertNull(failure.get());
      assertTrue(discoveryCompleted.get(), "cancellation returned before tree discovery completed");
      assertTrue(cleanupCompletedBeforeReturn.get(), "cancellation returned before survivor cleanup completed");
      assertEquals(1, terminationCount.get());
      assertTrue(process.forciblyDestroyed, "interruption skipped survivor cleanup");
      assertTrue(interruptRestored.get(), "cancellation did not restore its interrupt flag");
    } finally {
      allowDiscoveryToComplete.countDown();
      if (cancellation.isAlive()) {
        cancellation.interrupt();
        cancellation.join(5000);
      }
      callback.onProcessFinished(process);
    }
  }

  @Test
  public void gracefullyTerminatesRegisteredProcessOnlyOnce() {
    StubProcess process = new StubProcess(true);
    RecordingProcessTreeKiller processTreeKiller = new RecordingProcessTreeKiller(true);
    ProcessTimeoutCallback callback = callback(true, 5, processTreeKiller, 123L);

    callback.onProcessStarted(process);

    assertEquals(1, ProcessTimeoutCallback.terminateRunningProcesses());
    assertEquals(0, ProcessTimeoutCallback.terminateRunningProcesses());
    assertEquals(1, processTreeKiller.invocationCount);
  }

  private static void assertProcessTreeOperationsRunConcurrently(boolean gracefulTermination) {
    final int processCount = 3;
    CountDownLatch operationsStarted = new CountDownLatch(processCount);
    AtomicBoolean allOperationsConcurrent = new AtomicBoolean(true);
    List<StubProcess> processes = new ArrayList<>();
    for (int index = 0; index < processCount; index++) {
      StubProcess process = new StubProcess(true);
      processes.add(process);
      ProcessTimeoutCallback callback = callback(true, 0, (ignoredProcess, ignoredPid) -> {
        if (gracefulTermination) awaitOperationPeers(operationsStarted, allOperationsConcurrent);
        return new ProcessTimeoutCallback.ProcessTreeTermination() {
          @Override
          public boolean isAlive() {
            return process.isAlive();
          }

          @Override
          public void forceKill() {
            if (!gracefulTermination) awaitOperationPeers(operationsStarted, allOperationsConcurrent);
            process.destroyForcibly();
          }
        };
      }, (long) index);
      callback.onProcessStarted(process);
    }

    assertEquals(processCount, ProcessTimeoutCallback.terminateRunningProcesses(0));
    assertTrue(allOperationsConcurrent.get(),
               (gracefulTermination ? "SIGTERM" : "SIGKILL") + " operations waited for earlier process trees");
    for (StubProcess process : processes) {
      assertTrue(process.forciblyDestroyed, "termination returned before survivor cleanup completed");
    }
  }

  private static void awaitOperationPeers(CountDownLatch operationsStarted, AtomicBoolean allOperationsConcurrent) {
    operationsStarted.countDown();
    try {
      if (!operationsStarted.await(5, TimeUnit.SECONDS)) allOperationsConcurrent.set(false);
    } catch (InterruptedException e) {
      allOperationsConcurrent.set(false);
      Thread.currentThread().interrupt();
    }
  }

  private static ProcessTimeoutCallback callback(boolean gracefulTerminationEnabled,
                                                 int gracefulTerminationTimeoutSeconds,
                                                 ProcessTimeoutCallback.ProcessTreeKiller processTreeKiller,
                                                 Long pid) {
    return new ProcessTimeoutCallback(60,
                                      null,
                                      gracefulTerminationEnabled,
                                      gracefulTerminationTimeoutSeconds,
                                      process -> pid,
                                      processTreeKiller);
  }

  private static class RecordingProcessTreeKiller implements ProcessTimeoutCallback.ProcessTreeKiller {
    private final boolean terminateGracefully;
    private final String name;
    private final List<String> events;
    private Long pid;
    private int invocationCount;
    private int forceKillCount;

    private RecordingProcessTreeKiller() {
      this(true);
    }

    private RecordingProcessTreeKiller(boolean terminateGracefully) {
      this(terminateGracefully, null, null);
    }

    private RecordingProcessTreeKiller(boolean terminateGracefully, String name, List<String> events) {
      this.terminateGracefully = terminateGracefully;
      this.name = name;
      this.events = events;
    }

    @Override
    public ProcessTimeoutCallback.ProcessTreeTermination terminateGracefully(Process process, Long pid) {
      this.pid = pid;
      invocationCount++;
      if (events != null) {
        events.add("start-" + name);
      }
      if (terminateGracefully) {
        ((StubProcess) process).alive = false;
      }
      return new ProcessTimeoutCallback.ProcessTreeTermination() {
        @Override
        public boolean isAlive() {
          return process.isAlive();
        }

        @Override
        public void forceKill() {
          forceKillCount++;
          if (events != null) {
            events.add("force-" + name);
          }
          process.destroyForcibly();
        }
      };
    }
  }

  private static class StubProcess extends Process {
    private volatile boolean alive;
    private volatile boolean forciblyDestroyed;

    private StubProcess(boolean alive) {
      this.alive = alive;
    }

    @Override
    public OutputStream getOutputStream() {
      return new ByteArrayOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputStream getErrorStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() {
      alive = false;
      return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return !alive;
    }

    @Override
    public int exitValue() {
      if (alive) {
        throw new IllegalThreadStateException();
      }
      return 0;
    }

    @Override
    public void destroy() {
    }

    @Override
    public Process destroyForcibly() {
      forciblyDestroyed = true;
      alive = false;
      return this;
    }

    @Override
    public boolean isAlive() {
      return alive;
    }
  }
}
