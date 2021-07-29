package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitRepoOperations;
import jetbrains.buildServer.buildTriggers.vcs.git.command.NativeGitFetchCommand;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import org.jetbrains.annotations.NotNull;

public class GitRepoOperationsImpl implements GitRepoOperations {
  @NotNull final VcsRootSshKeyManager mySshKeyManager;
  private final ServerPluginConfig myConfig;
  private final FetchCommand myJGitFetchCommand;

  public GitRepoOperationsImpl(@NotNull ServerPluginConfig config, @NotNull VcsRootSshKeyManager sshKeyManager, @NotNull FetchCommand jGitFetchCommand) {
    myConfig = config;
    mySshKeyManager = sshKeyManager;
    myJGitFetchCommand = jGitFetchCommand;
  }

  @NotNull
  @Override
  public FetchCommand fetchCommand() {
    if (TeamCityProperties.getBooleanOrTrue("teamcity.git.nativeOperationsEnabled")) {
      return new NativeGitFetchCommand(myConfig, mySshKeyManager);
    }
    return myJGitFetchCommand;
  }
}
