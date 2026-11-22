package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.LocalClonePreparer;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class LocalClonePreparerTest extends BaseTestCase {

  public void preparations_of_a_clone_run_one_after_another() throws Exception {
    LocalClonePreparer preparer = new LocalClonePreparer();
    try {
      CountDownLatch firstStarted = new CountDownLatch(1);
      CountDownLatch releaseFirst = new CountDownLatch(1);
      AtomicBoolean secondRan = new AtomicBoolean();
      preparer.schedule(new File("clone"), () -> {
        firstStarted.countDown();
        await(releaseFirst);
      });
      CompletableFuture<Void> second = preparer.schedule(new File("clone"), () -> secondRan.set(true));
      CompletableFuture<Void> otherClone = preparer.schedule(new File("other clone"), () -> {});
      then(firstStarted.await(1, TimeUnit.MINUTES)).isTrue();

      otherClone.get(1, TimeUnit.MINUTES);
      then(secondRan.get()).as("the second preparation of the clone waits for the first one").isFalse();

      releaseFirst.countDown();

      second.get(1, TimeUnit.MINUTES);
      then(secondRan.get()).isTrue();
    } finally {
      preparer.shutdown();
    }
  }

  public void shutdown_fails_unfinished_preparations() throws Exception {
    setInternalProperty("teamcity.git.prepareForCollectingChanges.maxThreads", "1");
    LocalClonePreparer preparer = new LocalClonePreparer();
    CountDownLatch started = new CountDownLatch(1);
    CompletableFuture<Void> running = preparer.schedule(new File("clone"), () -> {
      started.countDown();
      await(new CountDownLatch(1));
    });
    CompletableFuture<Void> waitingForPreviousOne = preparer.schedule(new File("clone"), () -> {});
    then(started.await(1, TimeUnit.MINUTES)).isTrue();
    CompletableFuture<Void> waitingForThread = preparer.schedule(new File("other clone"), () -> {}); // the only thread is busy

    preparer.shutdown();

    then(running).isCompletedExceptionally();
    then(waitingForPreviousOne).isCompletedExceptionally();
    then(waitingForThread).isCompletedExceptionally();
  }

  public void preparation_scheduled_after_shutdown_fails() {
    LocalClonePreparer preparer = new LocalClonePreparer();
    preparer.shutdown();
    AtomicBoolean ran = new AtomicBoolean();

    CompletableFuture<Void> preparation = preparer.schedule(new File("clone"), () -> ran.set(true));

    then(preparation).isCompletedExceptionally();
    then(ran.get()).isFalse();
  }

  private static void await(@NotNull CountDownLatch latch) throws VcsException {
    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new VcsException("Interrupted", e);
    }
  }
}
