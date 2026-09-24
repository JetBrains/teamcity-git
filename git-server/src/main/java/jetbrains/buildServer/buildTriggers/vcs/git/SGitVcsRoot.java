package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.connections.ExpiringAccessToken;
import jetbrains.buildServer.serverSide.oauth.TokenRefresher;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.detectExtraHTTPCredentialsInVcsRoot;

public class SGitVcsRoot extends GitVcsRoot {

  @Nullable
  private final TokenRefresher myTokenRefresher;
  private final boolean myCheckProjectScope;

  public SGitVcsRoot(@NotNull MirrorManager mirrorManager,
                     @NotNull VcsRoot root,
                     @NotNull URIishHelper urIishHelper,
                     @Nullable TokenRefresher tokenRefresher) throws VcsException {
    super(mirrorManager, root, urIishHelper, detectExtraHTTPCredentialsInVcsRoot(root), tokenRefresher != null);

    checkUrlSafety();

    myTokenRefresher = tokenRefresher;
    myCheckProjectScope = (root.getId() >= 0);
  }

  private void checkUrlSafety() throws VcsException {
    GitRemoteUrlInspector.UrlRestriction fetchUrlRestriction = GitRemoteUrlInspector.verifyUrl(myRawFetchUrl);
    if (fetchUrlRestriction == GitRemoteUrlInspector.UrlRestriction.LOCAL_FILE_ACCESS) {
      throw new VcsException(String.format("VCS root '%s' is using local file fetch URL '%s', which is forbidden for security reasons. Please configure remote repository URLs to use network protocols like SSH or HTTPS.", getName(), myRawFetchUrl));
    } else if (fetchUrlRestriction == GitRemoteUrlInspector.UrlRestriction.DISALLOWED_TRANSPORT) {
      throw new VcsException(String.format("VCS root '%s': fetch URL transport not allowed: %s. %s", getName(), myRawFetchUrl, GitRemoteUrlInspector.getAllowedTransportsHint()));
    }

    GitRemoteUrlInspector.UrlRestriction pushUrlRestriction = GitRemoteUrlInspector.verifyUrl(myPushUrl);
    if (pushUrlRestriction == GitRemoteUrlInspector.UrlRestriction.LOCAL_FILE_ACCESS) {
      throw new VcsException(String.format("VCS root '%s' is using local file push URL '%s', which is forbidden for security reasons. Please configure remote repository URLs to use network protocols like SSH or HTTPS.", getName(), myPushUrl));
    } else if (pushUrlRestriction == GitRemoteUrlInspector.UrlRestriction.DISALLOWED_TRANSPORT) {
      throw new VcsException(String.format("VCS root '%s': push URL transport not allowed: %s. %s", getName(), myPushUrl, GitRemoteUrlInspector.getAllowedTransportsHint()));
    }
  }

  @Nullable
  protected ExpiringAccessToken getOrRefreshToken(@NotNull String tokenId) {
    VcsRoot vcsRoot = getOriginalRoot();
    if (myTokenRefresher == null)
      return null;

    SVcsRoot parentRoot = vcsRoot instanceof SVcsRoot ? (SVcsRoot)vcsRoot
                                                      : vcsRoot instanceof VcsRootInstance ? ((VcsRootInstance)vcsRoot).getParent() : null;
    if (parentRoot == null) {
      return myTokenRefresher.getToken(vcsRoot.getExternalId(), tokenId, myCheckProjectScope, true);
    } else {
      return myTokenRefresher.getToken(parentRoot.getProject(), tokenId, myCheckProjectScope, true);
    }
  }
}
