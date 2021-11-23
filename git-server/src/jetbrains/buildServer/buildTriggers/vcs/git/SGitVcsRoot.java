package jetbrains.buildServer.buildTriggers.vcs.git;

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

  public SGitVcsRoot(@NotNull MirrorManager mirrorManager,
                     @NotNull VcsRoot root,
                     @NotNull URIishHelper urIishHelper,
                     @Nullable TokenRefresher tokenRefresher) throws VcsException {
    super(mirrorManager, root, urIishHelper);
    myTokenRefresher = tokenRefresher;
  }

  @Override
  protected AuthSettings createAuthSettings(@NotNull URIishHelper urIishHelper) {
    return new AuthSettingsImpl(this, urIishHelper, tokenId -> getOrRefreshToken(tokenId));
  }

  private String getOrRefreshToken(@NotNull String tokenId) {
    VcsRoot vcsRoot = getOriginalRoot();
    if (myTokenRefresher == null)
      return tokenId;
    SVcsRoot parentRoot = vcsRoot instanceof SVcsRoot ? (SVcsRoot)vcsRoot
                                                      : vcsRoot instanceof VcsRootInstance ? ((VcsRootInstance)vcsRoot).getParent() : null;
    if (parentRoot == null) {
      return myTokenRefresher.getRefreshableTokenValue(vcsRoot.getExternalId(), tokenId);
    } else {
      return myTokenRefresher.getRefreshableTokenValue(parentRoot.getProject(), tokenId);
    }
  }
}
