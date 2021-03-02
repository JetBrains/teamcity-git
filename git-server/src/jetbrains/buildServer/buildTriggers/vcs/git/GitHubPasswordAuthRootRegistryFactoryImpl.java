package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.MultiNodesEvents;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.ServerResponsibility;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.TimeService;
import jetbrains.buildServer.vcs.RepositoryStateListener;
import org.jetbrains.annotations.NotNull;

public class GitHubPasswordAuthRootRegistryFactoryImpl implements GitHubPasswordAuthRootRegistryFactory {

  private static final GitHubPasswordAuthRootRegistry EMPTY_REGISTRY = new GitHubPasswordAuthRootRegistry() {
    @Override
    public boolean containsVcsRoot(long vcsRootId) {
      return false;
    }
  };

  @NotNull
  private final GitHubPasswordAuthRootRegistry myRegistry;

  public GitHubPasswordAuthRootRegistryFactoryImpl(@NotNull EventDispatcher<BuildServerListener> buildServerEventDispatcher,
                                                   @NotNull EventDispatcher<RepositoryStateListener> repositoryStateEventDispatcher,
                                                   @NotNull ProjectManager projectManager,
                                                   @NotNull ServerResponsibility serverResponsibility,
                                                   @NotNull MultiNodesEvents multiNodesEvents,
                                                   @NotNull TimeService timeService) {
    myRegistry = GitHubPasswordAuthRootRegistryFactory.isEnabled() ?
                 new GitHubPasswordAuthRootRegistryImpl(buildServerEventDispatcher, repositoryStateEventDispatcher, projectManager, serverResponsibility, multiNodesEvents,  timeService) :
                 EMPTY_REGISTRY
    ;
  }

  @NotNull
  @Override
  public GitHubPasswordAuthRootRegistry createRegistry() {
    return myRegistry;
  }
}
