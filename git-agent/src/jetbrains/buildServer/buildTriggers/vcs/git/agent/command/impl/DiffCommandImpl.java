

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.DiffCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class DiffCommandImpl extends BaseCommandImpl implements DiffCommand {

  private String myCommit1;
  private String myCommit2;
  private String myFormat;

  public DiffCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  @Override
  public DiffCommand setCommit1(@NotNull final String commit1) {
    myCommit1 = commit1;
    return this;
  }

  @NotNull
  @Override
  public DiffCommand setCommit2(@NotNull final String commit2) {
    myCommit2 = commit2;
    return this;
  }

  @NotNull
  @Override
  public DiffCommand setFormat(@NotNull final String format) {
    myFormat = format;
    return this;
  }

  @NotNull
  @Override
  public List<String> call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("diff");
    if (myFormat != null) {
      cmd.addParameter(myFormat);
    }
    if (myCommit1 != null) {
      cmd.addParameter(myCommit1);
    }
    if (myCommit2 != null) {
      cmd.addParameter(myCommit2);
    }

    ExecResult r = CommandUtil.runCommand(cmd);
    String stdout = r.getStdout().trim();
    return StringUtil.isEmpty(stdout) ? Collections.<String>emptyList() : Arrays.asList(StringUtil.splitByLines(stdout));
  }
}