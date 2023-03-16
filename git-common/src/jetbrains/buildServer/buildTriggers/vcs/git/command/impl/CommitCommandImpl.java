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

    String author = myAuthor.isEmpty() ? "Empty author <>" : myAuthor + " <" + myAuthor + "@TeamCity>";
    cmd.addParameter("--author=" + author);
    try {
      CommandUtil.runCommand(cmd.stdErrExpected(false));
    } catch (VcsException e) {
      //git commit returns non-zero exit code if there is no added files for commit. So looks like in this case we can safely ignore this problem
      //another option to change it - add --allow-empty flag in git commit parameter, but in this case git will create empty commit
      if (!e.getMessage().contains("nothing to commit")) {
        throw e;
      }
    }
  }
}
