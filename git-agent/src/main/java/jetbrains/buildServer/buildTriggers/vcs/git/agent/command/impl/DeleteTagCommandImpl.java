

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.DeleteTagCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class DeleteTagCommandImpl extends BaseCommandImpl implements DeleteTagCommand {

  private static final int TAG_PREFIX_LENGTH = "refs/tags/".length();
  private String myName;

  public DeleteTagCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public DeleteTagCommand setName(@NotNull String tagFullName) {
    if (tagFullName.length() < TAG_PREFIX_LENGTH)
      throw new IllegalArgumentException("Full tag name expected, " + tagFullName);
    myName = tagFullName.substring(TAG_PREFIX_LENGTH);
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("tag");
    cmd.addParameter("-d");
    cmd.addParameter(myName);
    CommandUtil.runCommand(cmd);
  }
}