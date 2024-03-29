package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.LsTreeCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.LsTreeResult;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class LsTreeCommandImpl extends BaseCommandImpl implements LsTreeCommand {

  private String path;
  private String revision;

  public LsTreeCommandImpl(@NotNull GitCommandLine myCmd) {
    super(myCmd);
  }

  @Override
  public LsTreeCommand setPath(final String path) {
    this.path = path;
    return this;
  }

  @Override
  public LsTreeCommand setRevision(String revision) {
    this.revision = revision;
    return this;
  }

  @Override
  public LsTreeResult call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("ls-tree");
    if (revision != null)
      cmd.addParameter(revision);
    if (path != null)
      cmd.addParameter(path);

    ExecResult r = CommandUtil.runCommand(cmd);
    String stdout = r.getStdout().trim();
    return StringUtil.isEmpty(stdout) ? null : parseLsTreeOutput(stdout);
  }

  /**
   * Expecting line to be formatted like '<mode> SPACE <type> SPACE <object> TAB <file>'
   */
  private LsTreeResult parseLsTreeOutput(String line) throws VcsException {
    final int tabIndex = line.indexOf("\t");
    if (tabIndex > 0) {
      String[] splitLine = line.substring(0, tabIndex).split(" ");
      if (splitLine.length == 3) {
        return new LsTreeResult(splitLine[0], splitLine[1], splitLine[2], line.substring(tabIndex));
      }
    }
    throw new VcsException("Cannot parse ls-tree output: '" + line + "', expected format '<mode> SPACE <type> SPACE <object> TAB <file>'");
  }
}
