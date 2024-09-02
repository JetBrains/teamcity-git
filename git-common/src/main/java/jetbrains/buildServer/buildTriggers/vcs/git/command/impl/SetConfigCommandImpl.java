package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.SetConfigCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class SetConfigCommandImpl extends BaseCommandImpl implements SetConfigCommand {
  private String myPropertyName;
  private String myValue;
  private boolean myUnSet = false;

  public SetConfigCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public SetConfigCommand setPropertyName(@NotNull String name) {
    myPropertyName = name;
    return this;
  }

  @NotNull
  public SetConfigCommand setValue(@NotNull String value) {
    myValue = value;
    return this;
  }

  @NotNull
  public SetConfigCommand unSet() {
    this.myUnSet = true;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    if (myUnSet) {
      cmd.addParameters("config", "--unset", myPropertyName);
    } else {
      cmd.addParameters("config", myPropertyName, myValue);
    }
    CommandUtil.runCommand(cmd.stdErrExpected(false));
  }
}
