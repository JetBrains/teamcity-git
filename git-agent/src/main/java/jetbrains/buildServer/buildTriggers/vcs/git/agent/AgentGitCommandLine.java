

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.SshHandler;
import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AgentGitCommandLine extends GitCommandLine {

  private final GitAgentSSHService mySsh;

  public AgentGitCommandLine(@Nullable GitAgentSSHService ssh,
                             @NotNull ScriptGen scriptGen,
                             @NotNull Context ctx) {
    super(ctx, scriptGen);
    mySsh = ssh;
  }

  @NotNull
  @Override
  protected ExecResult doRunCommand(@NotNull GitCommandSettings settings) throws VcsException {
    final AuthSettings authSettings = settings.getAuthSettings();
    if (authSettings == null || settings.isUseNativeSsh()) {
      return super.doRunCommand(settings);
    }

    if (mySsh == null)
      throw new IllegalStateException("Ssh is not initialized");

    final SshHandler h = new SshHandler(mySsh, mySshKeyManager, authSettings, this, myCtx);
    try {
      return super.doRunCommand(settings);
    } finally {
      h.unregister();
    }
  }

  public void setSshKeyManager(VcsRootSshKeyManager sshKeyManager) {
    mySshKeyManager = root -> {
      final TeamCitySshKey key = sshKeyManager.getKey(root);
      if (key == null) {
        myCtx.getLogger().warning("Failed to retrieve uploaded ssh key from server, agent default ssh key will be used");
      }
      return key;
    };
  }
}