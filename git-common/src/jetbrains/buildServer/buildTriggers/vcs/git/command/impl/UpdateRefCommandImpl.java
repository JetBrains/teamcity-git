

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.UpdateRefCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class UpdateRefCommandImpl extends BaseCommandImpl implements UpdateRefCommand {

  private String myRef;
  private String myOldValue;
  private String myRevision;
  private boolean myDelete;

  public UpdateRefCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public UpdateRefCommand setRef(@NotNull String ref) {
    myRef = ref;
    return this;
  }

  @NotNull
  public UpdateRefCommand setRevision(@NotNull String revision) {
    myRevision = revision;
    return this;
  }

  @NotNull
  @Override
  public UpdateRefCommand setOldValue(@NotNull String v) {
    myOldValue = v;
    return this;
  }

  @NotNull
  public UpdateRefCommand delete() {
    myDelete = true;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("update-ref");
    if (myDelete)
      cmd.addParameter("-d");
    cmd.addParameter(myRef);
    if (myRevision != null)
      cmd.addParameter(myRevision);
    if (myOldValue != null)
      cmd.addParameter(myOldValue);
    CommandUtil.runCommand(cmd.stdErrExpected(false));
  }
}