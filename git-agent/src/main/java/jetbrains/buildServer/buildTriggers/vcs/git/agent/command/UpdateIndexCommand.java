

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.AuthCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface UpdateIndexCommand extends BaseCommand, AuthCommand<UpdateIndexCommand> {

  /**
   * Sets the --really-refresh option
   * @param reallyRefresh if true the option will be added
   * @return this command
   */
  @NotNull
  UpdateIndexCommand reallyRefresh(boolean reallyRefresh);

  /**
   * Sets the -q option, so that command doesn't fail when it finds outdated index entry
   * @param quiet if true the option will be added
   * @return this command
   */
  @NotNull
  UpdateIndexCommand quiet(boolean quiet);


  void call() throws VcsException;

}