package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.File;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the fetches which prepare local clones for collecting changes, see {@link GitCollectChangesPolicy#prepareForCollectingChanges}.
 * <p>
 * The preparations of a local clone run one after another: fetches into a clone wait for its write lock anyway,
 * and a preparation waiting for the lock would occupy a thread which the preparations of other clones could use.
 * The first one waits for the lock not longer than {@link GitCollectChangesPolicy#PREPARE_WRITE_LOCK_TIMEOUT}.
 */
public class LocalClonePreparer {
  static final String MAX_THREADS_PROP = "teamcity.git.prepareForCollectingChanges.maxThreads";
  private static final int DEFAULT_MAX_THREADS = 4;
  private static final String THREAD_NAME_PREFIX = "Git preparing for collecting changes";

  private final ConcurrentMap<File, CompletableFuture<Void>> myLastPreparations = new ConcurrentHashMap<>(); // clone directory -> its last scheduled preparation
  private final Set<CompletableFuture<Void>> myPendingPreparations = ConcurrentHashMap.newKeySet();
  private ExecutorService myExecutor; // guarded by this
  private boolean myShutdown; // guarded by this

  /**
   * @param cloneDir the directory of the local clone the preparation fetches into
   * @return a future which completes after the preparation runs, it fails if the preparation fails or the preparer is shut down
   */
  @NotNull
  public CompletableFuture<Void> schedule(@NotNull File cloneDir, @NotNull VcsAction preparation) {
    ExecutorService executor = getExecutor();
    if (executor == null) return failedFuture(new VcsException("Cannot prepare for collecting changes: the Git plugin is unloaded"));

    CompletableFuture<Void> scheduled = myLastPreparations.compute(cloneDir, (dir, last) -> {
      CompletableFuture<Void> previous = last == null ? CompletableFuture.completedFuture(null) : last.handle((result, error) -> null);
      return previous.thenRunAsync(() -> run(preparation), executor);
    });
    myPendingPreparations.add(scheduled);
    scheduled.whenComplete((result, error) -> {
      myLastPreparations.remove(cloneDir, scheduled);
      myPendingPreparations.remove(scheduled);
    });
    return scheduled;
  }

  /**
   * Stops the threads, called when the plugin is unloaded. The preparations which have not completed yet fail,
   * so that nobody waits for them, and so do the preparations scheduled afterwards.
   */
  public void shutdown() {
    ExecutorService executor;
    synchronized (this) {
      myShutdown = true;
      executor = myExecutor;
    }
    if (executor != null) {
      ThreadUtil.shutdownGracefully(executor, THREAD_NAME_PREFIX);
    }

    VcsException error = new VcsException("Stopped preparing for collecting changes: the Git plugin is unloaded");
    for (CompletableFuture<Void> preparation : myPendingPreparations) {
      preparation.completeExceptionally(error);
    }
  }

  private static void run(@NotNull VcsAction preparation) {
    try {
      preparation.run();
    } catch (VcsException e) {
      throw new CompletionException(e);
    }
  }

  @Nullable
  private synchronized ExecutorService getExecutor() {
    if (myShutdown) return null;
    if (myExecutor == null) {
      myExecutor = ExecutorsFactory.newFixedDaemonExecutor(THREAD_NAME_PREFIX, TeamCityProperties.getInteger(MAX_THREADS_PROP, DEFAULT_MAX_THREADS));
    }
    return myExecutor;
  }

  @NotNull
  private static CompletableFuture<Void> failedFuture(@NotNull Throwable error) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    result.completeExceptionally(error);
    return result;
  }
}
