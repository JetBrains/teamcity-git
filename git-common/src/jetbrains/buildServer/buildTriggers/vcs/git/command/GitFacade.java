package jetbrains.buildServer.buildTriggers.vcs.git.command;

import org.jetbrains.annotations.NotNull;

public interface GitFacade {
  @NotNull
  FetchCommand fetch();

  @NotNull
  LsRemoteCommand lsRemote();
}
