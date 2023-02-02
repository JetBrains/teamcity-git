package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import java.util.ArrayList;
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.StatusCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil.splitByLines;

public class StatusCommandImpl extends BaseCommandImpl implements StatusCommand {

  public StatusCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @Override
  public StatusResult call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("status");
    cmd.addParameter("--porcelain=v1");
    cmd.addParameter("-b");
    return parse(CommandUtil.runCommand(cmd.stdErrLogLevel("debug")).getStdout());
  }

  private StatusResult parse(String output) {
    //see man git status for --porcelain v1 version for details
    String branchName = null;
    List<FileLine> modifiedFiles = new ArrayList<>();
    for (String line : splitByLines(output)) {
      if (line.startsWith("##")) {
        branchName = line.split(" ")[1].split("\\.\\.\\.")[0];
        continue;
      }
      String[] parts = line.split(" -> ");
      String statusCode = parts[0].substring(0, 2);
      String file = parts.length > 1 ? parts[1] : parts[0].substring(3);
      modifiedFiles.add(new FileLine(statusCode, file));
    }
    return new StatusResult(branchName, modifiedFiles);
  }
}
