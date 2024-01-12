

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.util.List;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

public interface LsRemoteCommand extends BaseCommand, AuthCommand<LsRemoteCommand> {

  @NotNull
  LsRemoteCommand peelRefs();

  @NotNull
  LsRemoteCommand setTags();

  @NotNull
  LsRemoteCommand setBranches(String ... branches);

  @NotNull
  List<Ref> call() throws VcsException;

}