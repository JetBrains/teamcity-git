package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.oauth.ExpiringAccessToken;
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
    super(mirrorManager, root, urIishHelper, tokenRefresher != null);
    myTokenRefresher = tokenRefresher;
  }

  @Nullable
  protected ExpiringAccessToken getOrRefreshToken(@NotNull String tokenId) {
    VcsRoot vcsRoot = getOriginalRoot();
    if (myTokenRefresher == null)
      return null;

    SVcsRoot parentRoot = vcsRoot instanceof SVcsRoot ? (SVcsRoot)vcsRoot
                                                      : vcsRoot instanceof VcsRootInstance ? ((VcsRootInstance)vcsRoot).getParent() : null;
    if (parentRoot == null) {
      return myTokenRefresher.getRefreshableToken(vcsRoot.getExternalId(), tokenId);
    } else {
      return myTokenRefresher.getRefreshableToken(parentRoot.getProject(), tokenId);
    }
  }
}
