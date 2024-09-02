

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface VersionCommand extends BaseCommand {

  @NotNull
  GitVersion call() throws VcsException;

}