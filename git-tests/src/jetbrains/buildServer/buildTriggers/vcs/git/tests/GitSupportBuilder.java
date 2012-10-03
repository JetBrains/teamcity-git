package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.cache.ResetCacheHandler;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GitSupportBuilder {

  private ServerPluginConfig myPluginConfig;
  private PluginConfigBuilder myPluginConfigBuilder;
  private ExtensionHolder myExtensionHolder;
  private ResetCacheRegister myResetCacheManager;
  private FetchCommand myFetchCommand;
  private ServerPaths myServerPaths;
  private RepositoryManager myRepositoryManager;
  private TransportFactory myTransportFactory;
  private MirrorManager myMirrorManager;
  private GitMapFullPath myMapFullPath;
  private List<GitServerExtension> myExtensions = new ArrayList<GitServerExtension>();

  public static GitSupportBuilder gitSupport() {
    return new GitSupportBuilder();
  }

  @NotNull
  public GitVcsSupport build() {
    if (myPluginConfigBuilder == null && myServerPaths == null)
      throw new IllegalStateException("Plugin config and server paths are not set");
    myPluginConfig = myPluginConfigBuilder != null ? myPluginConfigBuilder.build() : new PluginConfigImpl(myServerPaths);
    myTransportFactory = new TransportFactoryImpl(myPluginConfig);
    if (myFetchCommand == null)
      myFetchCommand = new FetchCommandImpl(myPluginConfig, myTransportFactory);
    myMirrorManager = new MirrorManagerImpl(myPluginConfig, new HashCalculatorImpl());
    myRepositoryManager = new RepositoryManagerImpl(myPluginConfig, myMirrorManager);
    final ResetCacheRegister resetCacheManager;
    if (myResetCacheManager == null) {
      Mockery context = new Mockery();
      resetCacheManager = context.mock(ResetCacheRegister.class);
      context.checking(new Expectations() {{
        allowing(resetCacheManager).registerHandler(with(any(ResetCacheHandler.class)));
      }});
    } else {
      resetCacheManager = myResetCacheManager;
    }
    myMapFullPath = new GitMapFullPath(myPluginConfig);
    return new GitVcsSupport(myPluginConfig, resetCacheManager, myTransportFactory, myFetchCommand, myRepositoryManager, myMapFullPath, myExtensionHolder, myExtensions);
  }

  public GitSupportBuilder withPluginConfig(@NotNull PluginConfigBuilder config) {
    myPluginConfigBuilder = config;
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

  public GitSupportBuilder withExtension(@NotNull GitServerExtension extension) {
    myExtensions.add(extension);
    return this;
  }

  public RepositoryManager getRepositoryManager() {
    return myRepositoryManager;
  }

  public GitMapFullPath getMapFullPath() {
    return myMapFullPath;
  }
}
