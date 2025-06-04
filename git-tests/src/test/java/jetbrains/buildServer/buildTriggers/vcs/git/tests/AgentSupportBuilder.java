

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.IOException;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.oauth.AgentTokenRetriever;
import jetbrains.buildServer.agent.oauth.AgentTokenStorage;
import jetbrains.buildServer.agent.oauth.InvalidAccessToken;
import jetbrains.buildServer.buildTriggers.vcs.git.HashCalculatorImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManagerImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.*;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.BuildAgentConfigurationBuilder;
import jetbrains.buildServer.connections.ExpiringAccessToken;
import jetbrains.buildServer.serverSide.impl.ssh.ServerSshKnownHostsManagerImpl;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

class AgentSupportBuilder {

  private final TempFiles myTempFiles;
  private MirrorManagerImpl myMirrorManager;
  private GitDetector myGitDetector;
  private BuildAgentConfiguration myAgentConfiguration;
  private PluginConfigFactoryImpl myPluginConfigFactory;
  private VcsRootSshKeyManagerProvider mySshKeyProvider;
  private GitMetaFactory myGitMetaFactory;
  private BuildAgent myAgent;
  private FS myFS;
  private GitAgentSSHService myGitAgentSSHService;

  AgentSupportBuilder(@NotNull TempFiles tempFiles) {
    myTempFiles = tempFiles;
  }


  @NotNull
  GitAgentVcsSupport build() throws IOException {
    GitPathResolver resolver = new MockGitPathResolver();
    if (myGitDetector == null)
      myGitDetector = new GitDetectorImpl(resolver);
    if (myAgentConfiguration == null)
      myAgentConfiguration = BuildAgentConfigurationBuilder.agentConfiguration(myTempFiles.createTempDir(), myTempFiles.createTempDir(), myTempFiles.createTempDir()).build();
    if (myPluginConfigFactory == null)
      myPluginConfigFactory = new PluginConfigFactoryImpl(myAgentConfiguration, myGitDetector);
    if (myMirrorManager == null)
      myMirrorManager = new MirrorManagerImpl(new AgentMirrorConfig(myAgentConfiguration), new HashCalculatorImpl(), new RemoteRepositoryUrlInvestigatorImpl());
    if (mySshKeyProvider == null)
      mySshKeyProvider = new MockVcsRootSshKeyManagerProvider();
    if (myGitMetaFactory == null)
      myGitMetaFactory = new GitMetaFactoryImpl();
    if (myAgent == null)
      myAgent = new MockBuildAgent();
    if (myFS == null)
      myFS = new FSImpl();
    CurrentBuildTracker buildTracker = new CurrentBuildTracker() {
      @NotNull
      @Override
      public AgentRunningBuild getCurrentBuild() throws NoRunningBuildException {
        throw new NoRunningBuildException();
      }

      @Override
      public boolean isRunningBuild() {
        return false;
      }
    };
    myGitAgentSSHService =
      new GitAgentSSHService(myAgentConfiguration, mySshKeyProvider);

    AgentTokenRetriever tokenRetriever = new AgentTokenRetriever() {
      @NotNull
      @Override
      public ExpiringAccessToken retrieveToken(@NotNull String tokenId) {
        return new InvalidAccessToken();
      }
    };
    return new GitAgentVcsSupport(myFS, new MockDirectoryCleaner(), myGitAgentSSHService,
                                  myPluginConfigFactory, myMirrorManager, new SubmoduleManagerImpl(myMirrorManager), myGitMetaFactory,
                                  EventDispatcher.create(AgentLifeCycleListener.class), new AgentTokenStorage(EventDispatcher.create(AgentLifeCycleListener.class), tokenRetriever),
                                  new ServerSshKnownHostsManagerImpl(null));
  }


  MirrorManagerImpl getMirrorManager() {
    return myMirrorManager;
  }


  AgentSupportBuilder setGitDetector(final GitDetector gitDetector) {
    myGitDetector = gitDetector;
    return this;
  }


  AgentSupportBuilder setSshKeyProvider(final VcsRootSshKeyManagerProvider sshKeyProvider) {
    mySshKeyProvider = sshKeyProvider;
    return this;
  }


  AgentSupportBuilder setGitMetaFactory(final GitMetaFactory gitMetaFactory) {
    myGitMetaFactory = gitMetaFactory;
    return this;
  }


  AgentSupportBuilder setFS(final FS FS) {
    myFS = FS;
    return this;
  }


  BuildAgentConfiguration getAgentConfiguration() {
    return myAgentConfiguration;
  }

  PluginConfigFactoryImpl getPluginConfigFactory() {
    return myPluginConfigFactory;
  }

  GitMetaFactory getGitMetaFactory() {
    return myGitMetaFactory;
  }

  GitAgentSSHService getGitAgentSSHService() {
    return myGitAgentSSHService;
  }
}