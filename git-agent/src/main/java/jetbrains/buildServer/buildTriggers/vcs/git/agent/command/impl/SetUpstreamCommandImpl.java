

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SetUpstreamCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class SetUpstreamCommandImpl extends BaseCommandImpl implements SetUpstreamCommand {
  private String myLocalBranch;
  private String myUpstreamBranch;

  public SetUpstreamCommandImpl(@NotNull GitCommandLine cmd,
                                @NotNull String localBranch,
                                @NotNull String upstreamBranch) {
    super(cmd);
    myLocalBranch = localBranch;
    myUpstreamBranch = upstreamBranch;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    GitVersion version = cmd.getGitVersion();
    if (version.isLessThan(new GitVersion(1, 7, 0))) {
      //ability to set upstream was added in 1.7.0
      return;
    } else if (version.isLessThan(new GitVersion(1, 8, 0))) {
      cmd.addParameters("branch", "--set-upstream", myLocalBranch, myUpstreamBranch);
    } else {
      cmd.addParameters("branch", "--set-upstream-to=" + myUpstreamBranch);
    }
    CommandUtil.runCommand(cmd.stdErrExpected(false));
  }
}