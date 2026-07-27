

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;

public interface PackRefs extends BaseCommand {
  PackRefs setErrorExpected(boolean expected);
  void call() throws VcsException;

}