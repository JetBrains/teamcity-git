package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

public interface RefCommit {
  @NotNull
  String getRef();

  @NotNull
  String getCommit();

  boolean isRefTip();
}
