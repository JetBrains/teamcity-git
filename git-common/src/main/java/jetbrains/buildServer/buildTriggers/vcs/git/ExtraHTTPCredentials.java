package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ExtraHTTPCredentials {
  @NotNull
  String getUrl();

  @Nullable
  String getUsername();

  @Nullable
  String getPassword();

  @Nullable
  String getToken();

  boolean isRefreshableToken();
}
