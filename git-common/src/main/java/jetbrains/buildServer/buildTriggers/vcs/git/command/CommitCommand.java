package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;

public interface CommitCommand extends BaseCommand {

  CommitCommand setComment(String comment);

  CommitCommand setAuthor(String author);

  void call() throws VcsException;

}
