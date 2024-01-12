

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.CloneCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class CloneCommandImpl extends BaseCommandImpl implements CloneCommand {

  private boolean myMirror = false;
  private String myRepo;
  private String myFolder;

  public CloneCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }


  @NotNull
  public CloneCommand setMirror(boolean mirror) {
    myMirror = mirror;
    return this;
  }

  @NotNull
  @Override
  public CloneCommand setRepo(@NotNull String repoUrl) {
    myRepo = repoUrl;
    return this;
  }

  @NotNull
  @Override
  public CloneCommand setFolder(@NotNull String folder) {
    myFolder = folder;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("clone");
    if (myMirror) {
      cmd.addParameter("--mirror");
    }
    cmd.addParameter(myRepo);
    if (StringUtil.isNotEmpty(myFolder)) {
      cmd.addParameter(myFolder);
    }
    CommandUtil.runCommand(cmd.stdErrExpected(false));
  }
}