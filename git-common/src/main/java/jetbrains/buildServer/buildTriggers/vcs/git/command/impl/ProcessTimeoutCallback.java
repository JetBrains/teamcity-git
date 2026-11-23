

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.processes.ProcessPid;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProcessTimeoutCallback extends SimpleCommandLineProcessRunner.ProcessRunCallbackAdapter {
  private static final Logger LOG = Logger.getInstance(ProcessTimeoutCallback.class);
  private static final Set<ProcessTimeoutCallback> RUNNING_CALLBACKS =
    Collections.newSetFromMap(new ConcurrentHashMap<ProcessTimeoutCallback, Boolean>());
  private static final long TERMINATION_POLL_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
  private static final ExecutorService TERMINATION_EXECUTOR = Executors.newCachedThreadPool(task -> {
    Thread thread = new Thread(task, "Native Git process-tree termination");
    thread.setDaemon(true);
    return thread;
  });

  public static final String GRACEFUL_TERMINATION_ENABLED = "teamcity.git.native.gracefulTermination.enabled";
  public static final String GRACEFUL_TERMINATION_TIMEOUT_SECONDS = "teamcity.git.native.gracefulTermination.timeout.seconds";
  static final int DEFAULT_GRACEFUL_TERMINATION_TIMEOUT_SECONDS = 5;
  static final int MAX_GRACEFUL_TERMINATION_TIMEOUT_SECONDS = 60;

  @Nullable private final Integer myTimeoutSeconds;
  @Nullable private final Integer myMaxAcceptedOutputSize;
  private final boolean myGracefulTerminationEnabled;
  private final int myGracefulTerminationTimeoutSeconds;
  @NotNull private final ProcessPidResolver myProcessPidResolver;
  @NotNull private final ProcessTreeKiller myProcessTreeKiller;

  @Nullable private volatile Process myProcess;
  @Nullable private volatile Long myProcessPid;
  private volatile boolean myProcessFinished;
  private final AtomicBoolean myTerminationStarted = new AtomicBoolean();

  public ProcessTimeoutCallback(int timeoutSeconds) {
    this(timeoutSeconds, null);
  }

  public ProcessTimeoutCallback(@Nullable final Integer timeoutSeconds, @Nullable final Integer maxAcceptedOutputSize) {
    this(timeoutSeconds,
         maxAcceptedOutputSize,
         SystemInfo.isUnix && TeamCityProperties.getBooleanOrTrue(GRACEFUL_TERMINATION_ENABLED),
         gracefulTerminationTimeoutSeconds(),
         new ProcessPidResolver() {
           @Nullable
           @Override
           public Long getPid(@NotNull Process process) {
             return resolveProcessPid(process);
           }
         },
         new RuntimeProcessTreeKiller());
  }

  ProcessTimeoutCallback(@Nullable final Integer timeoutSeconds,
                         @Nullable final Integer maxAcceptedOutputSize,
                         final boolean gracefulTerminationEnabled,
                         final int gracefulTerminationTimeoutSeconds,
                         @NotNull final ProcessPidResolver processPidResolver,
                         @NotNull final ProcessTreeKiller processTreeKiller) {
    myTimeoutSeconds = timeoutSeconds;
    myMaxAcceptedOutputSize = maxAcceptedOutputSize;
    myGracefulTerminationEnabled = gracefulTerminationEnabled;
    myGracefulTerminationTimeoutSeconds = gracefulTerminationTimeoutSeconds;
    myProcessPidResolver = processPidResolver;
    myProcessTreeKiller = processTreeKiller;
  }

  @Nullable
  @Override
  public Integer getOutputIdleSecondsTimeout() {
    return myTimeoutSeconds;
  }

  @Nullable
  @Override
  public Integer getMaxAcceptedOutputSize() {
    return myMaxAcceptedOutputSize;
  }

  @Override
  public void onProcessStarted(@NotNull Process process) {
    myProcess = process;
    try {
      myProcessPid = myProcessPidResolver.getPid(process);
    } catch (RuntimeException e) {
      LOG.warn("Could not determine native Git process id; process-tree termination will continue without PID logging: " +
               e.getMessage());
    }
    if (myGracefulTerminationEnabled) RUNNING_CALLBACKS.add(this);
  }

  @Override
  public void onProcessFinished(@NotNull Process process) {
    myProcessFinished = true;
    RUNNING_CALLBACKS.remove(this);
  }

  @Override
  public boolean terminateEntireProcessTree() {
    // TeamCity reads this flag before waiting and uses it for both output-idle timeouts and
    // thread interruption. Build cancellation is terminated gracefully by beforeBuildInterrupted;
    // keeping this enabled preserves descendant cleanup when an output-idle timeout fires.
    return true;
  }

  /**
   * Gracefully terminates all native Git commands currently tracked by the plugin. The agent invokes
   * this before TeamCity interrupts checkout so the complete, live process trees are still discoverable.
   * All trees share one grace deadline; after it expires, every survivor is force-killed.
   * Tree discovery and signal delivery run concurrently so their costs do not add up across trees.
   */
  public static int terminateRunningProcesses() {
    return terminateRunningProcesses(gracefulTerminationTimeoutSeconds());
  }

  static int terminateRunningProcesses(final int gracefulTerminationTimeoutSeconds) {
    return terminateProcessTrees(RUNNING_CALLBACKS.toArray(new ProcessTimeoutCallback[0]),
                                 gracefulTerminationTimeoutSeconds);
  }

  /**
   * Propagates SIGTERM to the process tree, waits once for the configured grace period, and sends
   * SIGKILL to survivors. This is also called after command execution as a fallback for failures
   * outside the runner's normal process-tree cleanup.
   */
  boolean terminateProcessTreeIfRunning() {
    return terminateProcessTrees(new ProcessTimeoutCallback[] {this}, myGracefulTerminationTimeoutSeconds) == 1;
  }

  private static int terminateProcessTrees(@NotNull ProcessTimeoutCallback[] callbacks,
                                           final int gracefulTerminationTimeoutSeconds) {
    final long deadlineNanos = System.nanoTime() +
      TimeUnit.SECONDS.toNanos(Math.max(0, gracefulTerminationTimeoutSeconds));
    final AtomicBoolean interrupted = new AtomicBoolean(Thread.interrupted());
    try {
      final List<Future<ProcessTreeTermination>> starts = new ArrayList<>();
      for (ProcessTimeoutCallback callback : callbacks) {
        starts.add(TERMINATION_EXECUTOR.submit(callback::startProcessTreeTerminationIfRunning));
      }

      final List<ProcessTreeTermination> terminations = new ArrayList<>();
      for (Future<ProcessTreeTermination> start : starts) {
        final ProcessTreeTermination termination = awaitTerminationTask(start, interrupted);
        if (termination != null) terminations.add(termination);
      }

      // Complete every tree snapshot and SIGTERM phase before allowing SIGKILL. Returning while
      // discovery is still running would let TeamCity kill roots and orphan undiscovered children.
      final List<Future<?>> finishes = new ArrayList<>();
      for (ProcessTreeTermination termination : terminations) {
        finishes.add(TERMINATION_EXECUTOR.submit(() -> waitAndForceKill(termination, deadlineNanos, interrupted)));
      }
      for (Future<?> finish : finishes) {
        awaitTerminationTask(finish, interrupted);
      }
      return terminations.size();
    } finally {
      if (interrupted.get()) Thread.currentThread().interrupt();
    }
  }

  private static void waitAndForceKill(@NotNull ProcessTreeTermination termination,
                                       final long deadlineNanos,
                                       @NotNull AtomicBoolean interrupted) {
    try {
      while (!interrupted.get()) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) break;
        if (!termination.isAlive()) return;
        remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) break;
        TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, TERMINATION_POLL_INTERVAL_NANOS));
      }
    } catch (InterruptedException e) {
      interrupted.set(true);
    }
    if (termination.isAlive()) termination.forceKill();
  }

  @Nullable
  private static <T> T awaitTerminationTask(@NotNull Future<T> task, @NotNull AtomicBoolean interrupted) {
    while (true) {
      try {
        return task.get();
      } catch (InterruptedException e) {
        // Finish cleanup before TeamCity interrupts checkout, then restore the caller's flag.
        interrupted.set(true);
      } catch (ExecutionException e) {
        LOG.warn("Native Git process-tree termination task failed", e.getCause());
        return null;
      }
    }
  }

  @Nullable
  private ProcessTreeTermination startProcessTreeTerminationIfRunning() {
    final Process process = myProcess;
    if (!myGracefulTerminationEnabled || myProcessFinished || process == null || !process.isAlive()) {
      RUNNING_CALLBACKS.remove(this);
      return null;
    }
    if (!myTerminationStarted.compareAndSet(false, true)) return null;
    RUNNING_CALLBACKS.remove(this);

    final Long processPid = myProcessPid;
    LOG.info("Gracefully terminating native Git process tree" +
             (processPid == null ? "" : " rooted at " + processPid) +
             "; SIGKILL will follow after the shared grace deadline if needed");
    try {
      return myProcessTreeKiller.terminateGracefully(process, processPid);
    } catch (RuntimeException e) {
      LOG.warn("Could not terminate native Git process tree" +
               (processPid == null ? "" : " rooted at " + processPid) + ": " + e.getMessage() +
               "; using the root process fallback");
      process.destroy();
      return new RootProcessTreeTermination(process);
    }
  }

  /**
   * Uses {@code Process.pid()} when the runtime provides it while preserving Java 8 bytecode
   * compatibility for older TeamCity installations.
   */
  @Nullable
  static Long resolveProcessPid(@NotNull Process process) {
    final Method pidMethod;
    try {
      pidMethod = Process.class.getMethod("pid");
    } catch (NoSuchMethodException e) {
      return ProcessPid.getPid(process, "native Git command");
    } catch (SecurityException e) {
      LOG.warn("Could not access Process.pid(); falling back to TeamCity's process id resolver: " + e.getMessage());
      return ProcessPid.getPid(process, "native Git command");
    }

    try {
      final Object pid = pidMethod.invoke(process);
      if (pid instanceof Long) return (Long) pid;
      LOG.warn("Process.pid() returned an unexpected value; falling back to TeamCity's process id resolver");
    } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
      LOG.warn("Could not invoke Process.pid(); falling back to TeamCity's process id resolver: " + e.getMessage());
    }
    return ProcessPid.getPid(process, "native Git command");
  }

  private static int gracefulTerminationTimeoutSeconds() {
    final int configured = TeamCityProperties.getInteger(GRACEFUL_TERMINATION_TIMEOUT_SECONDS,
                                                          DEFAULT_GRACEFUL_TERMINATION_TIMEOUT_SECONDS);
    return Math.max(1, Math.min(MAX_GRACEFUL_TERMINATION_TIMEOUT_SECONDS, configured));
  }

  interface ProcessPidResolver {
    @Nullable
    Long getPid(@NotNull Process process);
  }

  interface ProcessTreeKiller {
    @NotNull
    ProcessTreeTermination terminateGracefully(@NotNull Process process, @Nullable Long pid);
  }

  interface ProcessTreeTermination {
    boolean isAlive();

    void forceKill();
  }

  private static final class RuntimeProcessTreeKiller implements ProcessTreeKiller {
    @NotNull
    @Override
    public ProcessTreeTermination terminateGracefully(@NotNull Process process, @Nullable Long pid) {
      try {
        return ReflectiveProcessTreeTermination.start(process);
      } catch (ReflectiveOperationException | RuntimeException e) {
        throw new IllegalStateException("ProcessHandle tree termination is unavailable", e);
      }
    }
  }

  private static final class ReflectiveProcessTreeTermination implements ProcessTreeTermination {
    @NotNull private final Process myRootProcess;
    @NotNull private final List<Object> myProcessHandles;
    @NotNull private final Method myIsAliveMethod;
    @NotNull private final Method myDestroyForciblyMethod;

    private ReflectiveProcessTreeTermination(@NotNull Process rootProcess,
                                             @NotNull List<Object> processHandles,
                                             @NotNull Method isAliveMethod,
                                             @NotNull Method destroyForciblyMethod) {
      myRootProcess = rootProcess;
      myProcessHandles = processHandles;
      myIsAliveMethod = isAliveMethod;
      myDestroyForciblyMethod = destroyForciblyMethod;
    }

    @NotNull
    private static ReflectiveProcessTreeTermination start(@NotNull Process rootProcess)
      throws ReflectiveOperationException {
      final Class<?> processHandleClass = Class.forName("java.lang.ProcessHandle");
      final Method toHandleMethod = Process.class.getMethod("toHandle");
      final Method descendantsMethod = processHandleClass.getMethod("descendants");
      final Method isAliveMethod = processHandleClass.getMethod("isAlive");
      final Method destroyMethod = processHandleClass.getMethod("destroy");
      final Method destroyForciblyMethod = processHandleClass.getMethod("destroyForcibly");
      final Object rootHandle = toHandleMethod.invoke(rootProcess);
      final List<Object> processHandles = new ArrayList<>();

      try (Stream<?> descendants = (Stream<?>) descendantsMethod.invoke(rootHandle)) {
        descendants.forEach(processHandles::add);
      }
      processHandles.add(rootHandle);

      for (Object processHandle : processHandles) {
        try {
          destroyMethod.invoke(processHandle);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
          LOG.warn("Could not gracefully terminate a native Git process: " + e.getMessage());
        }
      }
      return new ReflectiveProcessTreeTermination(rootProcess,
                                                  processHandles,
                                                  isAliveMethod,
                                                  destroyForciblyMethod);
    }

    @Override
    public boolean isAlive() {
      for (Object processHandle : myProcessHandles) {
        try {
          if (Boolean.TRUE.equals(myIsAliveMethod.invoke(processHandle))) return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
          LOG.warn("Could not determine whether a native Git process is still alive: " + e.getMessage());
          return true;
        }
      }
      return false;
    }

    @Override
    public void forceKill() {
      for (Object processHandle : myProcessHandles) {
        try {
          myDestroyForciblyMethod.invoke(processHandle);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
          LOG.warn("Could not force-kill a native Git process: " + e.getMessage());
        }
      }
      if (myRootProcess.isAlive()) myRootProcess.destroyForcibly();
    }
  }

  private static final class RootProcessTreeTermination implements ProcessTreeTermination {
    @NotNull private final Process myProcess;

    private RootProcessTreeTermination(@NotNull Process process) {
      myProcess = process;
    }

    @Override
    public boolean isAlive() {
      return myProcess.isAlive();
    }

    @Override
    public void forceKill() {
      if (myProcess.isAlive()) myProcess.destroyForcibly();
    }
  }
}
