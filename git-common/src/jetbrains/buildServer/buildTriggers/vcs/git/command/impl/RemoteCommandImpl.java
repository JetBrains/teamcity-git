package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.RemoteCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class RemoteCommandImpl extends BaseAuthCommandImpl<RemoteCommand> implements RemoteCommand {
  private String myName = "origin";
  private String myCommand;

  public RemoteCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  @Override
  public RemoteCommand setRemote(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  @Override
  public RemoteCommand setCommand(@NotNull String command) {
    myCommand = command;
    return this;
  }

  @Override
  public void call() throws VcsException {
    if (myCommand == null) {
      throw new IllegalStateException("Please specify command");
    }

    final GitCommandLine cmd = getCmd();
    cmd.addParameters("remote", myCommand, myName);
    runCmd(cmd);
  }
}
