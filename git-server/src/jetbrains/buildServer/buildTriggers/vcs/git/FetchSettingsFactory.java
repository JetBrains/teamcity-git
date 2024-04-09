package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Collection;
import java.util.Set;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.NotNull;

public interface FetchSettingsFactory {
  @NotNull
  FetchSettings getFetchSettings(@NotNull OperationContext context, @NotNull Collection<RefCommit> refsToFetch,
                                 @NotNull Collection<RefCommit> revisions, @NotNull Set<String> remoteRefs, boolean includeTags) throws VcsException;

  @NotNull
  FetchSettings getFetchSettings(@NotNull OperationContext context, boolean includeTags) throws VcsException;

}
