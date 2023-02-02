package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.command.CommitCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class CommitCommandImpl extends BaseCommandImpl implements CommitCommand {

  private String myComment;
  private String myAuthor;

  public CommitCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @Override
  public CommitCommand setComment(String comment) {
    myComment = comment;
    return this;
  }

  @Override
  public CommitCommand setAuthor(String author) {
    myAuthor = author;
    return this;
  }

  @Override
  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("commit");
    cmd.addParameter("-m");
    cmd.addParameter(myComment.isEmpty() ? "<Empty comment>" : myComment);

    String author = myAuthor.isEmpty() ? "Empty author <>" : myAuthor + " <>";
    cmd.addParameter("--author=" + author);
    CommandUtil.runCommand(cmd.stdErrExpected(false));
  }
}
