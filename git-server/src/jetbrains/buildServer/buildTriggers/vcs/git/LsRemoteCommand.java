package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Map;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

public interface LsRemoteCommand {
  @NotNull
  Map<String, Ref> lsRemote(@NotNull Repository db, @NotNull GitVcsRoot gitRoot) throws VcsException;
}
