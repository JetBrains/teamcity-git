

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.InitCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.InitCommandResult;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class InitCommandImpl extends BaseCommandImpl implements InitCommand {

  public final static GitVersion GIT_INIT_STDERR_DEFAULT_BRANCH_HINT = new GitVersion(2, 30, 0);

  public static final String INITIAL_BRANCH = "main";

  private boolean myBare = false;

  public InitCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }


  @NotNull
  public InitCommand setBare(boolean bare) {
    myBare = bare;
    return this;
  }


  @NotNull
  public InitCommandResult call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("init");
    if (myBare)
      cmd.addParameter("--bare");
    String initialBranch = "master";
    if (!cmd.getGitVersion().isLessThan(GIT_INIT_STDERR_DEFAULT_BRANCH_HINT)) {
      // TW-69468
      cmd.addParameter("--initial-branch=" + INITIAL_BRANCH);
      initialBranch = INITIAL_BRANCH;
    }
    CommandUtil.runCommand(cmd.stdErrExpected(false));
    return new InitCommandResult(initialBranch, false);
  }
}