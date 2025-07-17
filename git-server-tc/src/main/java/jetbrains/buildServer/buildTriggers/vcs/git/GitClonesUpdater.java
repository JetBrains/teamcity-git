

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.Disposable;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;

public class GitClonesUpdater {
  private final ConcurrentHashMap<VcsRoot, RepositoryStateData> myScheduledForUpdate = new ConcurrentHashMap<>();
  private final GitVcsSupport myVcs;
  private final RepositoryManager myRepositoryManager;
  private final ServerResponsibility myServerResponsibility;
  private volatile ExecutorService myExecutor;

  public GitClonesUpdater(@NotNull EventDispatcher<RepositoryStateListener> eventDispatcher,
                          @NotNull EventDispatcher<BuildServerListener> serverEventDispatcher,
                          @NotNull ServerResponsibility serverResponsibility,
                          @NotNull GitVcsSupport gitVcsSupport,
                          @NotNull RepositoryManager repositoryManager) {
    myVcs = gitVcsSupport;
    myRepositoryManager = repositoryManager;
    myServerResponsibility = serverResponsibility;

    eventDispatcher.addListener(new RepositoryStateListenerAdapter() {
      @Override
      public void repositoryStateChanged(@NotNull final VcsRoot root,
                                         @NotNull final RepositoryState oldState,
                                         @NotNull final RepositoryState newState) {
        if (serverResponsibility.canCheckForChanges() && CurrentNodeInfo.getNodeId().equals(newState.getLastUpdatedBy())) return;

        if (!TeamCityProperties.getBooleanOrTrue("teamcity.git.localClones.updateIfNoCheckingForChangesResponsibility")) return;

        if (root.getVcsName().equals(Constants.VCS_NAME)) {
          if (myScheduledForUpdate.size() > TeamCityProperties.getInteger("teamcity.git.localClones.maxScheduledUpdates", 1000) && !myScheduledForUpdate.containsKey(root)) {
            Loggers.VCS.warn("Cannot schedule update of the local clone for: " + LogUtil.describe(root) + " because the number of already scheduled updates is too big");
            return;
          }

          myScheduledForUpdate.put(root, RepositoryStateFactory.toData(newState));
          synchronized (myScheduledForUpdate) {
            if (myExecutor == null) {
              myExecutor = ExecutorsFactory.newFixedDaemonExecutor("Git local clones updater",
                                                                   TeamCityProperties.getInteger("teamcity.git.localClones.maxParallelUpdateThreads", 2),
                                                                   TeamCityProperties.getInteger("teamcity.git.localClones.maxPoolSize", 2),
                                                                   TeamCityProperties.getInteger("teamcity.git.localClones.maxQueueSize", 5));
            }
          }
          if (myExecutor.isShutdown()) return;

          try {
            myExecutor.submit(GitClonesUpdater.this::processVcsRootsScheduledForUpdate);
          } catch (RejectedExecutionException ignored) {
            //if the exception occured it means there are already submitted tasks which will process our change, so we can ignore this exception
          }
        }
      }
    });

    serverEventDispatcher.addListener(new BuildServerAdapter() {
      @Override
      public void serverShutdown() {
        if (myExecutor != null) {
          ThreadUtil.shutdownGracefully(myExecutor, "Git local clones updater");
        }
      }
    });
  }

  private void processVcsRootsScheduledForUpdate() {
    Set<VcsRoot> vcsRoots = new HashSet<>(myScheduledForUpdate.keySet());
    for (VcsRoot root: vcsRoots) {
      RepositoryStateData state = myScheduledForUpdate.remove(root);
      if (state == null) continue;
      if (myServerResponsibility.canCheckForChanges()) {
        // do nothing as this node now has responsibility to collect changes and will update repositories during the checking for changes process
        continue;
      }

      OperationContext context = myVcs.createContext(root, "updating local clone");
      try {
        IOGuard.allowNetworkAndCommandLine(() -> {
          GitVcsRoot gitRoot = context.getGitRoot();
          Disposable threadName = NamedThreadFactory.patchThreadName("Updating local clone directory: " + gitRoot.getRepositoryDir() + " (" + gitRoot.getRepositoryFetchURL().toString() + ")");
          try {
            myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
              ReentrantLock writeLock = myRepositoryManager.getWriteLock(context.getRepository().getDirectory());
              if (!writeLock.tryLock()) return; // do nothing because another process already took a write lock and it's not that important for us to perform this fetch

              try {
                new FetchContext(context, myVcs).withToRevisions(state.getBranchRevisions()).fetchIfNoCommitsOrFail();
              } catch (Exception e) {
                throw context.wrapException(e);
              } finally {
                writeLock.unlock();
              }
            });
          } finally {
            threadName.dispose();
          }
        });
      } catch (VcsException e) {
        Loggers.VCS.warnAndDebugDetails("Could not update local clone for: " + LogUtil.describe(root), e);
      } finally {
        context.close();
      }
    }
  }
}