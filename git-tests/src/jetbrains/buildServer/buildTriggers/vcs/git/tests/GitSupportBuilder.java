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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitRepoOperationsImpl;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.oauth.OAuthToken;
import jetbrains.buildServer.serverSide.oauth.TokenRefresher;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.cache.ResetCacheHandler;
import jetbrains.buildServer.util.cache.ResetCacheRegister;
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

  public GitMapFullPath getMapFullPath() {
    return myMapFullPath;
  }

  public CommitLoader getCommitLoader() {
    return myCommitLoader;
  }

  public TransportFactory getTransportFactory() {
    return myTransportFactory;
  }

  @NotNull
  public GitVcsSupport build() {
    if (myPluginConfigBuilder == null && myServerPaths == null && myPluginConfig == null)
      throw new IllegalStateException("Plugin config or server paths should be set");
    if (myPluginConfig == null)
      myPluginConfig = myPluginConfigBuilder != null ? myPluginConfigBuilder.build() : new PluginConfigImpl(myServerPaths);
    if (myTransportFactory == null)
      myTransportFactory = new TransportFactoryImpl(myPluginConfig, myVcsRootSSHKeyManager);

    Mockery context = new Mockery();
    if (myFetchCommand == null) {
      if (myBeforeFetchHook == null) {
        myFetchCommand = new FetchCommandImpl(myPluginConfig, myTransportFactory, new FetcherProperties(myPluginConfig), myVcsRootSSHKeyManager);
      } else {
        final FetchCommand originalCommand = new FetchCommandImpl(myPluginConfig, myTransportFactory, new FetcherProperties(myPluginConfig), myVcsRootSSHKeyManager);
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
    final GitRepoOperations gitRepoOperations = myGitRepoOperations == null ? new GitRepoOperationsImpl(myPluginConfig, myTransportFactory, myVcsRootSSHKeyManager, myFetchCommand) : myGitRepoOperations;
    myCommitLoader = new CommitLoaderImpl(myRepositoryManager, gitRepoOperations, myMapFullPath, myPluginConfig);
    GitResetCacheHandler resetCacheHandler = new GitResetCacheHandler(myRepositoryManager, new GcErrors());
    ResetRevisionsCacheHandler resetRevisionsCacheHandler = new ResetRevisionsCacheHandler(revisionsCache);

    TokenRefresher tokenRefresher = new TokenRefresher() {
      @Nullable
      @Override
      public OAuthToken getRefreshableToken(@NotNull String vcsRootExtId, @NotNull String tokenFullId) {
        return null;
      }

      @Nullable
      @Override
      public OAuthToken getRefreshableToken(@NotNull SProject project, @NotNull String tokenFullId) {
        return null;
      }
    };

    GitVcsSupport git = new GitVcsSupport(gitRepoOperations, myPluginConfig, resetCacheManager, myTransportFactory, myRepositoryManager, myMapFullPath, myCommitLoader,
                                          myVcsRootSSHKeyManager, new MockVcsOperationProgressProvider(),
                                          resetCacheHandler, resetRevisionsCacheHandler, tokenRefresher, myTestConnectionSupport);
    git.addExtensions(myExtensions);
    git.setExtensionHolder(myExtensionHolder);
    return git;
  }
}
