/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jmock.lib.legacy.ClassImposteriser;

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

  public static GitSupportBuilder gitSupport() {
    return new GitSupportBuilder();
  }

  @NotNull
  public GitVcsSupport build() {
    if (myPluginConfigBuilder == null && myServerPaths == null && myPluginConfig == null)
      throw new IllegalStateException("Plugin config or server paths should be set");
    if (myPluginConfig == null)
      myPluginConfig = myPluginConfigBuilder != null ? myPluginConfigBuilder.build() : new PluginConfigImpl(myServerPaths);
    if (myTransportFactory == null)
      myTransportFactory = new TransportFactoryImpl(myPluginConfig);
    if (myFetchCommand == null)
      myFetchCommand = new FetchCommandImpl(myPluginConfig, myTransportFactory);
    myMirrorManager = new MirrorManagerImpl(myPluginConfig, new HashCalculatorImpl());
    myRepositoryManager = new RepositoryManagerImpl(myPluginConfig, myMirrorManager);
    final ResetCacheRegister resetCacheManager;
    if (myResetCacheManager == null) {
      Mockery context = new Mockery();
      context.setImposteriser(ClassImposteriser.INSTANCE);
      resetCacheManager = context.mock(ResetCacheRegister.class);
      context.checking(new Expectations() {{
        allowing(resetCacheManager).registerHandler(with(any(ResetCacheHandler.class)));
      }});
    } else {
      resetCacheManager = myResetCacheManager;
    }
    myMapFullPath = new GitMapFullPath(myPluginConfig);
    return new GitVcsSupport(myPluginConfig, resetCacheManager, myTransportFactory, myFetchCommand, myRepositoryManager, myMapFullPath, myExtensionHolder);
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

  public GitSupportBuilder withRepositoryManager(@NotNull RepositoryManager repositoryManager) {
    return this;
  }

  public GitSupportBuilder withFetchCommand(@NotNull FetchCommand fetchCommand) {
    myFetchCommand = fetchCommand;
    return this;
  }

  public GitSupportBuilder withTransportFactory(@NotNull TransportFactory factory) {
    myTransportFactory = factory;
    return this;
  }

  public RepositoryManager getRepositoryManager() {
    return myRepositoryManager;
  }

  public GitMapFullPath getMapFullPath() {
    return myMapFullPath;
  }
}
