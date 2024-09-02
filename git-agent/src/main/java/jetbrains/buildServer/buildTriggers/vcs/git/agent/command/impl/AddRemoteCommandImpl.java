

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.AddRemoteCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class AddRemoteCommandImpl extends BaseCommandImpl implements AddRemoteCommand {

  private String myName;
  private String myUrl;

  public AddRemoteCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public AddRemoteCommand setName(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  public AddRemoteCommand setUrl(@NotNull String url) {
    myUrl = url;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameters("remote", "add", myName, myUrl);
    CommandUtil.runCommand(cmd.stdErrExpected(false));
  }
}