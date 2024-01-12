

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.VersionCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class VersionCommandImpl extends BaseCommandImpl implements VersionCommand {

  public VersionCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public GitVersion call() throws VcsException {
    GitCommandLine cmd = getCmd().repeatOnEmptyOutput(true).stdErrExpected(false);
    cmd.addParameter("version");
    return GitVersion.parse(CommandUtil.runCommand(cmd, 60).getStdout());
  }
}