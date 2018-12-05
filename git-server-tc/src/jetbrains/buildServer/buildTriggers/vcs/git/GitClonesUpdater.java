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

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.ServerResponsibility;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class GitClonesUpdater {
  private final ConcurrentHashMap<VcsRoot, RepositoryStateData> myScheduledForUpdate = new ConcurrentHashMap<>();
  private final GitVcsSupport myVcs;
  private final RepositoryManager myRepositoryManager;
  private volatile ExecutorService myExecutor;

  public GitClonesUpdater(@NotNull EventDispatcher<RepositoryStateListener> eventDispatcher,
                          @NotNull EventDispatcher<BuildServerListener> serverEventDispatcher,
                          @NotNull ServerResponsibility serverResponsibility,
                          @NotNull GitVcsSupport gitVcsSupport,
                          @NotNull RepositoryManager repositoryManager) {
    myVcs = gitVcsSupport;
    myRepositoryManager = repositoryManager;

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

      OperationContext context = myVcs.createContext(root, "updating local clone");
      try {
        GitVcsRoot gitRoot = context.getGitRoot();
        myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
          Repository repo = context.getRepository();
          try {
            myVcs.getCollectChangesPolicy().ensureRepositoryStateLoadedFor(context, repo, true, state);
          } catch (Exception e1) {
            throw new VcsException(e1);
          }
        });
      } catch (VcsException e1) {
        Loggers.VCS.warnAndDebugDetails("Could not update local clone for: " + LogUtil.describe(root), e1);
      } finally {
        context.close();
      }
    }
  }
}
