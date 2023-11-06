package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.connections.ExpiringAccessToken;
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
  private final boolean myCheckProjectScope;

  public SGitVcsRoot(@NotNull MirrorManager mirrorManager,
                     @NotNull VcsRoot root,
                     @NotNull URIishHelper urIishHelper,
                     @Nullable TokenRefresher tokenRefresher) throws VcsException {
    super(mirrorManager, root, urIishHelper, tokenRefresher != null);
    myTokenRefresher = tokenRefresher;
    myCheckProjectScope = (root.getId() >= 0);

    AuthSettings authSettings = getAuthSettings();
    if (isTokenRefreshEnabled() &&
        authSettings.getAuthMethod().isPasswordBased() &&
        authSettings.isStoredTokenAuth() &&
        authSettings.getTokenId() != null &&
        !checkIsTokenPermitted(authSettings.getTokenId())) {
      throw new VcsException("VCS Root credentials refer to the token that is not permitted within the project scope");
    }
  }

  @Nullable
  private SVcsRoot getParentVcsRoot(@NotNull VcsRoot vcsRoot) {
    return vcsRoot instanceof SVcsRoot ? (SVcsRoot)vcsRoot
                                : vcsRoot instanceof VcsRootInstance ? ((VcsRootInstance)vcsRoot).getParent() : null;
  }

  @Nullable
  protected ExpiringAccessToken getOrRefreshToken(@NotNull String tokenId) {
    if (myTokenRefresher == null)
      return null;

    VcsRoot vcsRoot = getOriginalRoot();
    SVcsRoot parentRoot = getParentVcsRoot(vcsRoot);

    if (parentRoot == null) {
      return myTokenRefresher.getRefreshableToken(vcsRoot.getExternalId(), tokenId, myCheckProjectScope);
    } else {
      return myTokenRefresher.getRefreshableToken(parentRoot.getProject(), tokenId, myCheckProjectScope);
    }
  }

  private boolean checkIsTokenPermitted(@NotNull String tokenId) {
    if (myTokenRefresher == null)
      return false;

    VcsRoot vcsRoot = getOriginalRoot();
    SVcsRoot parentRoot = getParentVcsRoot(vcsRoot);

    if (parentRoot == null) {
      return myTokenRefresher.isTokenPermittedInVcsRoot(vcsRoot.getExternalId(), tokenId);
    }
    else {
      return myTokenRefresher.isTokenPermittedInProject(parentRoot.getProject(), tokenId);
    }
  }
}
