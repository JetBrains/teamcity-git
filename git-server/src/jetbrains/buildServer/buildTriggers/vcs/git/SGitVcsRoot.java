package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.text.StringUtil;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SGitVcsRoot extends GitVcsRoot {

  @Nullable
  private final OAuthTokensStorage myTokenStorage;
  private AuthSettings myResolvedAuthSettings = null;


  public SGitVcsRoot(@NotNull MirrorManager mirrorManager,
                     @NotNull VcsRoot root,
                     @NotNull URIishHelper urIishHelper,
                     @Nullable OAuthTokensStorage tokenStorage) throws VcsException {
    super(mirrorManager, root, urIishHelper);
    myTokenStorage = tokenStorage;
  }

  @Override
  public CommonURIish getRepositoryFetchURL() {
    return myURIishHelper.createAuthURI(getAuthSettings(), super.getRepositoryFetchURL());
  }

  @Override
  public CommonURIish getRepositoryFetchURLNoFixedErrors() {
    return myURIishHelper.createAuthURI(getAuthSettings(), super.getRepositoryFetchURLNoFixedErrors(), false);
  }

  @Override
  public CommonURIish getRepositoryPushURL() {
    return StringUtil.isEmpty(myPushUrl) ? getRepositoryFetchURL() : myURIishHelper.createAuthURI(getAuthSettings(), super.getRepositoryPushURL());
  }

  @Override
  public CommonURIish getRepositoryPushURLNoFixedErrors() {
    return StringUtil.isEmpty(myPushUrl) ? getRepositoryFetchURLNoFixedErrors() : myURIishHelper.createAuthURI(getAuthSettings(), super.getRepositoryPushURL(), false);
  }

  @NotNull
  @Override
  public AuthSettings getAuthSettings() {
    AuthSettings authSettings = super.getAuthSettings();
    String password = authSettings.getPassword();
    if (myTokenStorage == null || password == null || !password.startsWith("oauth2:")) {
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
    if (myTokenStorage == null)
      return suspectedTokenId;
    SVcsRoot parentRoot = vcsRoot instanceof SVcsRoot ? (SVcsRoot)vcsRoot
                                                      : vcsRoot instanceof VcsRootInstance ? ((VcsRootInstance)vcsRoot).getParent() : null;
    if (parentRoot == null) {
      return myTokenStorage.getOrRefreshToken(vcsRoot.getExternalId(), suspectedTokenId, suspectedTokenId);
    } else {
      return myTokenStorage.getOrRefreshToken(parentRoot.getProject(), suspectedTokenId, suspectedTokenId);
    }
  }
}
