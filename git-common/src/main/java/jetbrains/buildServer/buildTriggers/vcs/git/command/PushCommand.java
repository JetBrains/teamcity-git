package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface PushCommand extends BaseCommand, AuthCommand<PushCommand> {

  @NotNull
  PushCommand setRefspec(@NotNull String refspec);

  @NotNull
  PushCommand setRemote(@NotNull String remoteUrl);

  /**
   * Makes the push a compare-and-swap on the remote side: it is rejected unless the ref still points to the
   * expected revision, which also covers the cases a plain push accepts silently, like a ref deleted or rewound
   * remotely after it was read.
   *
   * @param ref ref to protect
   * @param expectedRevision revision the ref is expected to point to
   */
  @NotNull
  PushCommand setForceWithLease(@NotNull String ref, @NotNull String expectedRevision);

  void call() throws VcsException;
}
