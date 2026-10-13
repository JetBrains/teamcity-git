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
import java.util.List;
import java.util.concurrent.TimeUnit;
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
    List<String> events = new ArrayList<>();
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
  public void gracefullyTerminatesRegisteredProcessOnlyOnce() {
    StubProcess process = new StubProcess(true);
    RecordingProcessTreeKiller processTreeKiller = new RecordingProcessTreeKiller(true);
    ProcessTimeoutCallback callback = callback(true, 5, processTreeKiller, 123L);

    callback.onProcessStarted(process);

    assertEquals(1, ProcessTimeoutCallback.terminateRunningProcesses());
    assertEquals(0, ProcessTimeoutCallback.terminateRunningProcesses());
    assertEquals(1, processTreeKiller.invocationCount);
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
    private boolean alive;
    private boolean forciblyDestroyed;

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
