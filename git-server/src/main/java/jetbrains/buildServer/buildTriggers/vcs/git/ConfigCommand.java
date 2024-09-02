package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitConfigCommand;
import jetbrains.buildServer.vcs.VcsException;

public interface ConfigCommand extends GitCommand {

  void addConfigParameter(String path, GitConfigCommand.Scope scope, String name, String value) throws VcsException;

  void removeConfigParameter(String path, GitConfigCommand.Scope scope, String name) throws VcsException;

}
