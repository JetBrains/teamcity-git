package jetbrains.buildServer.buildTriggers.vcs.git.command;

import org.jetbrains.annotations.NotNull;

public interface GitFacade {

  @NotNull
  VersionCommand version();

  @NotNull
  FetchCommand fetch();

  @NotNull
  LsRemoteCommand lsRemote();

  @NotNull
  RemoteCommand remote();
}
