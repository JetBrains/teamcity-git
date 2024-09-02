package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.ListConfigCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class ListConfigCommandImpl extends BaseCommandImpl implements ListConfigCommand {
  public ListConfigCommandImpl(@NotNull final GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  @Override
  public String call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameters("config", "--list");
    if (!cmd.getGitVersion().isLessThan(new GitVersion(2, 8, 0))) {
      cmd.addParameter("--show-origin");
    }
    return CommandUtil.runCommand(cmd).getStdout().trim();
  }
}
