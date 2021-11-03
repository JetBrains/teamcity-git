package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.text.StringUtil;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.serverSide.oauth.TokenRefresher;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SGitVcsRoot extends GitVcsRoot {

  @Nullable
  private final TokenRefresher myTokenRefresher;
  private AuthSettings myResolvedAuthSettings = null;


  public SGitVcsRoot(@NotNull MirrorManager mirrorManager,
                     @NotNull VcsRoot root,
                     @NotNull URIishHelper urIishHelper,
                     @Nullable TokenRefresher tokenRefresher) throws VcsException {
    super(mirrorManager, root, urIishHelper);
    myTokenRefresher = tokenRefresher;
  }

  @NotNull
  @Override
  public AuthSettings getAuthSettings() {
    AuthSettings authSettings = super.getAuthSettings();
    if (authSettings.getAuthMethod() != AuthenticationMethod.PASSWORD) {
      return authSettings;
    }
    String password = authSettings.getPassword();
    if (myTokenRefresher == null || password == null || !password.startsWith("tc_token_id:")) {
      return authSettings;
    }
    Map<String, String> newProps = new HashMap<>();
    VcsRoot vcsRoot = getOriginalRoot();

    String newToken = getOrRefreshToken(vcsRoot, password);
    if (myResolvedAuthSettings != null) {
      String oldToken = myResolvedAuthSettings.getPassword();
      if (oldToken != null && oldToken.equals(newToken)) {
        return myResolvedAuthSettings;
      }
    }

    getProperties().forEach((k, v) -> {
      if (k.equals(Constants.PASSWORD)) {
        newProps.put(k, newToken);
      } else {
        newProps.put(k, v);
      }
    });
    return myResolvedAuthSettings = new AuthSettings(newProps, vcsRoot, myURIishHelper);
  }

  private String getOrRefreshToken(@NotNull VcsRoot vcsRoot, @NotNull String suspectedTokenId) {
    if (myTokenRefresher == null)
      return suspectedTokenId;
    SVcsRoot parentRoot = vcsRoot instanceof SVcsRoot ? (SVcsRoot)vcsRoot
                                                      : vcsRoot instanceof VcsRootInstance ? ((VcsRootInstance)vcsRoot).getParent() : null;
    if (parentRoot == null) {
      return myTokenRefresher.getOrRefreshToken(vcsRoot.getExternalId(), suspectedTokenId, suspectedTokenId);
    } else {
      return myTokenRefresher.getOrRefreshToken(parentRoot.getProject(), suspectedTokenId, suspectedTokenId);
    }
  }
}
