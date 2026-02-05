package jetbrains.buildServer.buildTriggers.vcs.git.tests.util;

import jetbrains.buildServer.buildTriggers.vcs.git.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.GitCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.LsRemoteCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.TransportFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitRepoOperationsImpl;
import jetbrains.buildServer.ssh.SshKnownHostsManager;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class TestGitRepoOperationsImpl extends GitRepoOperationsImpl {

  private FetchCommand myNativeGitFetchCommand;
  private LsRemoteCommand myLsRemoteCommand;


  public TestGitRepoOperationsImpl(@NotNull final ServerPluginConfig config,
                                   @NotNull final TransportFactory transportFactory,
                                   @NotNull final VcsRootSshKeyManager sshKeyManager,
                                   @NotNull final FetchCommand jGitFetchCommand,
                                   @NotNull final SshKnownHostsManager sshKnownHostsManager) {
    super(config, transportFactory, sshKeyManager, jGitFetchCommand, sshKnownHostsManager);
  }

  public TestGitRepoOperationsImpl withModifiedNativeGitFetchCommand(@NotNull final FetchCommand fetchCommand) {
    myNativeGitFetchCommand = fetchCommand;
    return this;
  }

  public TestGitRepoOperationsImpl withLsRemoteCommand(@NotNull final LsRemoteCommand lsRemoteCommand) {
    myLsRemoteCommand = lsRemoteCommand;
    return this;
  }

  /**
   * @return custom git fetch command if native git is enabled
   */
  @Override
  protected Optional<GitCommand> getNativeGitFetchOptional(@NotNull final String repoUrl) {
    if (myNativeGitFetchCommand == null) {
      return super.getNativeGitFetchOptional(repoUrl);
    } else {
      if (super.getNativeGitFetchOptional(repoUrl).isPresent()) {
        return Optional.of(myNativeGitFetchCommand);
      } else {
        return Optional.empty();
      }
    }
  }

  @NotNull
  @Override
  public LsRemoteCommand lsRemoteCommand(@NotNull String repoUrl) {
    if (myLsRemoteCommand != null) {
      return myLsRemoteCommand;
    }
    return super.lsRemoteCommand(repoUrl);
  }
}
