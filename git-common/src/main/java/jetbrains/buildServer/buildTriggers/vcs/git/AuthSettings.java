

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.List;
import java.util.Map;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AuthSettings {

  @Nullable
  VcsRoot getRoot();

  @NotNull
  AuthenticationMethod getAuthMethod();

  boolean isIgnoreKnownHosts();

  @Nullable
  String getPassphrase();

  @Nullable
  String getUserName();

  @Nullable
  String getPrivateKeyFilePath();

  @Nullable
  String getPassword();

  @Nullable
  String getTeamCitySshKeyId();

  @NotNull
  Map<String, String> toMap();

  boolean doesTokenNeedRefresh();

  @NotNull
  GitCommandCredentials getExtraHTTPCredentials();

  /**
   * @return true if {@link AuthenticationMethod#ACCESS_TOKEN} is used and the token was obtained recently {@link Constants#FRESH_TOKEN_PERIOD}
   */
  boolean isFreshToken();
}