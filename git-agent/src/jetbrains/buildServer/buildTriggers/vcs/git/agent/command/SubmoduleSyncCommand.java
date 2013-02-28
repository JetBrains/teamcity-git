package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.vcs.VcsException;

public interface SubmoduleSyncCommand {

  void call() throws VcsException;

}
