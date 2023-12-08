package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

public interface ExtraHTTPCredentials {
  @NotNull
  String getUrl();

  @NotNull
  String getUsername();

  @NotNull
  String getPassword();

  boolean isRefreshableToken();
}
