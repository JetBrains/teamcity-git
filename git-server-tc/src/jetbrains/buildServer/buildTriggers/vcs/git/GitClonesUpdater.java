/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.Disposable;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.NotNull;

public class GitClonesUpdater {
  private final ConcurrentHashMap<VcsRoot, RepositoryStateData> myScheduledForUpdate = new ConcurrentHashMap<>();
  private final GitVcsSupport myVcs;
  private final RepositoryManager myRepositoryManager;
  private final CommitLoader myCommitLoader;
  private final ServerResponsibility myServerResponsibility;
  private volatile ExecutorService myExecutor;

  public GitClonesUpdater(@NotNull EventDispatcher<RepositoryStateListener> eventDispatcher,
                          @NotNull EventDispatcher<BuildServerListener> serverEventDispatcher,
                          @NotNull ServerResponsibility serverResponsibility,
                          @NotNull GitVcsSupport gitVcsSupport,
                          @NotNull RepositoryManager repositoryManager,
                          @NotNull CommitLoader commitLoader) {
    myVcs = gitVcsSupport;
    myRepositoryManager = repositoryManager;
    myCommitLoader = commitLoader;
    myServerResponsibility = serverResponsibility;

    eventDispatcher.addListener(new RepositoryStateListenerAdapter() {
      @Override
      public void repositoryStateChanged(@NotNull final VcsRoot root,
                                         @NotNull final RepositoryState oldState,
                                         @NotNull final RepositoryState newState) {
        if (serverResponsibility.canCheckForChanges()) return;

        if (!TeamCityProperties.getBooleanOrTrue("teamcity.git.localClones.updateIfNoCheckingForChangesResponsibility")) return;

        if (root.getVcsName().equals(Constants.VCS_NAME)) {
          myScheduledForUpdate.put(root, RepositoryStateFactory.toData(newState));
          synchronized (myScheduledForUpdate) {
            if (myExecutor == null) {
              myExecutor = ExecutorsFactory.newFixedDaemonExecutor("Git local clones updater",
                                                                   TeamCityProperties.getInteger("teamcity.git.localClones.maxParallelUpdateThreads", 2));
            }
          }
          myExecutor.submit(GitClonesUpdater.this::processVcsRootsScheduledForUpdate);
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
                List<RefSpec> refspecs = getRefSpecs(state);
                runFetch(context, gitRoot, refspecs);
              } catch (Throwable e) {
                if (e instanceof VcsException) throw (VcsException)e;
                throw new VcsException(e);
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

  private void runFetch(@NotNull OperationContext context, @NotNull GitVcsRoot gitRoot, @NotNull List<RefSpec> refspecs) throws IOException, VcsException {
    Disposable n = NamedThreadFactory.patchThreadName("Performing fetch for " + refspecs.size() + " ref specs");
    try {
      myCommitLoader.fetch(context.getRepository(), gitRoot.getRepositoryFetchURL().get(), refspecs, new FetchSettings(gitRoot.getAuthSettings()));
    } finally {
      n.dispose();
    }
  }

  @NotNull
  private List<RefSpec> getRefSpecs(@NotNull RepositoryStateData state) {
    List<RefSpec> refspecs = new ArrayList<>();
    for (String branch: state.getBranchRevisions().keySet()) {
      final String expandedRef = GitUtils.expandRef(branch);
      refspecs.add(new RefSpec().setSource(expandedRef).setDestination(expandedRef).setForceUpdate(true));
    }
    return refspecs;
  }
}
