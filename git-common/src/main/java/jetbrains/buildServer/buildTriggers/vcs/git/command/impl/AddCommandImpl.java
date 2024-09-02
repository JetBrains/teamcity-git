package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.git.command.AddCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class AddCommandImpl extends BaseCommandImpl implements AddCommand {

  private final List<String> myPaths = new ArrayList<>();
  private boolean myAddAll = false;

  public AddCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @Override
  public AddCommand setPaths(List<String> paths) {
    myPaths.clear();
    myPaths.addAll(paths);
    return this;
  }

  @Override
  public AddCommand setAddAll(boolean addAll) {
    myAddAll = addAll;
    return this;
  }

  @Override
  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("add");
    if (myAddAll)
      cmd.addParameter("-A");
    cmd.addParameters(myPaths);
    CommandUtil.runCommand(cmd.stdErrExpected(false));
  }
}
