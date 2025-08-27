

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.command.NativeGitCommands;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.ChangesCollectorCache;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.util.TestGitRepoOperationsImpl;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.crypt.*;
import jetbrains.buildServer.serverSide.impl.ssh.ConstantServerSshKnownHostsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthToken;
import jetbrains.buildServer.serverSide.oauth.TokenRefresher;
import jetbrains.buildServer.serverSide.parameters.ParameterDescriptionFactoryImpl;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.serverSide.parameters.ParameterFactoryImpl;
import jetbrains.buildServer.serverSide.parameters.types.ParameterTypeManager;
import jetbrains.buildServer.serverSide.reEncryption.EncryptionEventDispatcher;
import jetbrains.buildServer.ssh.SshKnownHostsManager;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.cache.ResetCacheHandler;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.MockVcsOperationProgressProvider;
import jetbrains.buildServer.vcs.TestConnectionSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;

public class GitSupportBuilder {

  private ServerPluginConfig myPluginConfig;
  private PluginConfigBuilder myPluginConfigBuilder;
  private ExtensionHolder myExtensionHolder;
  private ResetCacheRegister myResetCacheManager;
  private FetchCommand myFetchCommand;
  private Runnable myBeforeFetchHook;
  private ServerPaths myServerPaths;
  private RepositoryManager myRepositoryManager;
  private TransportFactory myTransportFactory;
  private TestConnectionSupport myTestConnectionSupport;
  private GitMapFullPath myMapFullPath;
  private CommitLoader myCommitLoader;
  private final List<GitServerExtension> myExtensions = new ArrayList<GitServerExtension>();
  private VcsRootSshKeyManager myVcsRootSSHKeyManager = new EmptyVcsRootSshKeyManager();
  private GitRepoOperations myGitRepoOperations;
  private final SshKnownHostsManager myKnownHostsManager = new ConstantServerSshKnownHostsManager();

  public static GitSupportBuilder gitSupport() {
    return new GitSupportBuilder();
  }

  @NotNull
  public ServerPluginConfig getPluginConfig() {
    if (myPluginConfig == null) throw new Error("Plugin config is not yet created. Call #build()");
    return myPluginConfig;
  }

  public GitSupportBuilder withPluginConfig(@NotNull PluginConfigBuilder config) {
    myPluginConfigBuilder = config;
    return this;
  }

  public GitSupportBuilder withPluginConfig(@NotNull ServerPluginConfig config) {
    myPluginConfig = config;
    return this;
  }

  public GitSupportBuilder withServerPaths(@NotNull ServerPaths paths) {
    myServerPaths = paths;
    return this;
  }

  public GitSupportBuilder withExtensionHolder(@Nullable ExtensionHolder holder) {
    myExtensionHolder = holder;
    return this;
  }

  public GitSupportBuilder withResetCacheManager(@NotNull ResetCacheRegister resetCacheManager) {
    myResetCacheManager = resetCacheManager;
    return this;
  }

  public GitSupportBuilder withFetchCommand(@NotNull FetchCommand fetchCommand) {
    myFetchCommand = fetchCommand;
    return this;
  }

  public GitSupportBuilder withTestConnectionSupport(@NotNull TestConnectionSupport testConnection) {
    myTestConnectionSupport = testConnection;
    return this;
  }

  public GitSupportBuilder withExtension(@NotNull GitServerExtension extension) {
    myExtensions.add(extension);
    return this;
  }

  public GitSupportBuilder withSSHKeyManager(@NotNull VcsRootSshKeyManager sshKeyManager) {
    myVcsRootSSHKeyManager = sshKeyManager;
    return this;
  }

  public GitSupportBuilder withBeforeFetchHook(@NotNull Runnable beforeFetchHook) {
    myBeforeFetchHook = beforeFetchHook;
    return this;
  }

  public GitSupportBuilder withTransportFactory(@NotNull TransportFactory factory) {
    myTransportFactory = factory;
    return this;
  }

  public GitSupportBuilder withGitRepoOperations(@NotNull GitRepoOperations gitRepoOperations) {
    myGitRepoOperations = gitRepoOperations;
    return this;
  }

  public RepositoryManager getRepositoryManager() {
    return myRepositoryManager;
  }

  public GitRepoOperations getGitRepoOperations() { return myGitRepoOperations; }

  public GitMapFullPath getMapFullPath() {
    return myMapFullPath;
  }

  public CommitLoader getCommitLoader() {
    return myCommitLoader;
  }

  public TransportFactory getTransportFactory() {
    return myTransportFactory;
  }

  public ParameterFactory getParametersFactory(ServerResponsibility serverResponsibility, SettingsPersister settingsPersister) {
    final BaseEncryptionStrategy defaultEncryptionStrategy = new BaseEncryptionStrategy();
    return new ParameterFactoryImpl(new ParameterDescriptionFactoryImpl(), new ParameterTypeManager(Collections.emptyList()), new EncryptionManager(new EncryptionSettings(),
                                                                                                                                                    myServerPaths, settingsPersister, new EncryptionEventDispatcher(), serverResponsibility,
                                                                                                                                                    new EncryptionKeysStorageProvider(myServerPaths),
                                                                                                                                                    new CoreEncryptionManager(
                                                                                                                                                      Collections.emptyList(),
                                                                                                                                                      defaultEncryptionStrategy)));
  }

  public FetchCommand getDefaultFetchCommand() {
    init();
    if (isNativeGitEnabled()) {
      return new NativeGitCommands(myPluginConfig, () -> new GitExec("git", new GitVersion(2, 34, 0)), myVcsRootSSHKeyManager, null, myKnownHostsManager);
    } else {
      return new FetchCommandImpl(myPluginConfig, myTransportFactory, new FetcherProperties(myPluginConfig), myVcsRootSSHKeyManager, myKnownHostsManager);
    }
  }

  public static boolean isNativeGitEnabled() {
    return TeamCityProperties.getBoolean("teamcity.git.nativeOperationsEnabled");
  }

  private void init() {
    if (myPluginConfigBuilder == null && myServerPaths == null && myPluginConfig == null)
      throw new IllegalStateException("Plugin config or server paths should be set");
    if (myPluginConfig == null)
      myPluginConfig = myPluginConfigBuilder != null ? myPluginConfigBuilder.build() : new PluginConfigImpl(myServerPaths);
    if (myTransportFactory == null)
      myTransportFactory = new TransportFactoryImpl(myPluginConfig, myVcsRootSSHKeyManager, myKnownHostsManager);
  }

  @NotNull
  public GitVcsSupport build() {
    init();

    Mockery context = new Mockery();
    if (myFetchCommand == null) {
      if (myBeforeFetchHook == null) {
        myFetchCommand = getDefaultFetchCommand();
      } else {
        final FetchCommand originalCommand = getDefaultFetchCommand();
        myFetchCommand = (db, fetchURI, settings) -> {
          myBeforeFetchHook.run();
          originalCommand.fetch(db, fetchURI, settings);
        };
      }
    }
    MirrorManager mirrorManager = new MirrorManagerImpl(myPluginConfig, new HashCalculatorImpl(), new RemoteRepositoryUrlInvestigatorImpl());
    myRepositoryManager = new RepositoryManagerImpl(myPluginConfig, mirrorManager);
    final ResetCacheRegister resetCacheManager;
    if (myResetCacheManager == null) {
      context.setImposteriser(ClassImposteriser.INSTANCE);
      resetCacheManager = context.mock(ResetCacheRegister.class);
      context.checking(new Expectations() {{
        allowing(resetCacheManager).registerHandler(with(any(ResetCacheHandler.class)));
      }});
    } else {
      resetCacheManager = myResetCacheManager;
    }
    RevisionsCache revisionsCache = new RevisionsCache(myPluginConfig);
    myMapFullPath = new GitMapFullPath(myPluginConfig, revisionsCache);

    if (myGitRepoOperations == null) {
      myGitRepoOperations =
        new TestGitRepoOperationsImpl(myPluginConfig, myTransportFactory, myVcsRootSSHKeyManager, myFetchCommand, myKnownHostsManager);
      if (isNativeGitEnabled()) {
        ((TestGitRepoOperationsImpl)myGitRepoOperations).withModifiedNativeGitFetchCommand(myFetchCommand);
      }
    }

    myCommitLoader = new CommitLoaderImpl(myRepositoryManager, myGitRepoOperations, myMapFullPath, myPluginConfig, new FetchSettingsFactoryImpl());
    GitResetCacheHandler resetCacheHandler = new GitResetCacheHandler(myRepositoryManager, new GcErrors());
    ResetRevisionsCacheHandler resetRevisionsCacheHandler = new ResetRevisionsCacheHandler(revisionsCache);

    TokenRefresher tokenRefresher = new TokenRefresher() {

      @Nullable
      @Override
      public OAuthToken getToken(@Nullable SProject project, @NotNull String tokenFullId, boolean checkProjectScope, boolean refreshIfExpired) {
        return null;
      }

      @Nullable
      @Override
      public OAuthToken getToken(@NotNull String vcsRootExtId, @NotNull String tokenFullId, boolean checkProjectScope, boolean refreshIfExpired) {
        return null;
      }
    };

    ServerResponsibility serverResponsibility = context.mock(ServerResponsibility.class);
    SettingsPersister settingsPersister = context.mock(SettingsPersister.class);

    context.checking(new Expectations() {{
      allowing(serverResponsibility).canWriteToConfigDirectory(); will(returnValue(false));
    }});

    GitVcsSupport git = new GitVcsSupport(myGitRepoOperations, myPluginConfig, resetCacheManager, myTransportFactory, myRepositoryManager, myMapFullPath, myCommitLoader,
                                          myVcsRootSSHKeyManager, new MockVcsOperationProgressProvider(),
                                          resetCacheHandler, resetRevisionsCacheHandler, tokenRefresher, myTestConnectionSupport,
                                          new CheckoutRulesLatestRevisionCache(), new SSLTrustStoreProvider() {
      @Nullable
      @Override
      public KeyStore getTrustStore() {
        return null;
      }
    }, getParametersFactory(serverResponsibility, settingsPersister), new ChangesCollectorCache());
    git.addExtensions(myExtensions);
    git.setExtensionHolder(myExtensionHolder);
    return git;
  }
}