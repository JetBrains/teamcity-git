package jetbrains.buildServer.buildTriggers.vcs.git;

import com.google.common.collect.ImmutableMap;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.serverSide.changeViewers.ExternalChangeViewerExtension;
import jetbrains.buildServer.serverSide.changeViewers.PropertyType;
import jetbrains.buildServer.serverSide.impl.auth.SecuredVcsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.space.SpaceConnectDescriber;
import jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthProvider;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpaceExternalChangeViewerExtension implements ExternalChangeViewerExtension {

  private final SecuredVcsManager myVcsRootsManager;
  private final OAuthConnectionsManager myOAuthConnectionsManager;

  public SpaceExternalChangeViewerExtension(
    @NotNull ExtensionHolder extensionHolder,
    @NotNull SecuredVcsManager securedVcsManager,
    @NotNull OAuthConnectionsManager oAuthConnectionsManager
  ) {
    extensionHolder.registerExtension(ExternalChangeViewerExtension.class, getClass().getName(), this);
    myVcsRootsManager = securedVcsManager;
    myOAuthConnectionsManager = oAuthConnectionsManager;
  }

  @Nullable
  @Override
  public Map<String, String> getAvailableProperties(@NotNull VcsRoot vcs) {
    final VcsRootInstance vcsRoot = (VcsRootInstance)vcs;

    if (!Constants.VCS_NAME.equals(vcsRoot.getVcsName())) return null;
    String url = vcsRoot.getProperty(Constants.FETCH_URL);
    if (url == null) return null;

    URIish uri;
    try {
      uri = new URIish(url);
    } catch (URISyntaxException e) {
      return null;
    }

    final String gitPath = uri.getPath();

    if (uri.getHost().endsWith("jetbrains.space")) {
      // Space is on the Jetbrains side
      final String[] strings = gitPath.substring(1).split("/");
      if (strings.length == 3) {
        final String orgName = strings[0];
        final String project = strings[1];
        final String repository = strings[2].replace(".git", "");
        final String repositoryUrl = String.format("https://%s.jetbrains.space/p/%s/repositories/%s", orgName, project, repository);

        return createResponse(repositoryUrl);
      }
    } else {
      final SVcsRoot vcsRootParent = vcsRoot.getParent();
      final List<OAuthConnectionDescriptor> connections = myOAuthConnectionsManager.getAvailableConnectionsOfType(vcsRootParent.getProject(), SpaceOAuthProvider.TYPE);
      if (connections.isEmpty()) return null;

      for (OAuthConnectionDescriptor connection : connections) {
        final SpaceConnectDescriber spaceConnectDescriber = new SpaceConnectDescriber(connection);
        final String spaceAddress = spaceConnectDescriber.getAddress();

        if (uri.getHost().contains(spaceAddress)) {
          // Selfhosted Space
          final String[] strings = gitPath.substring(1).split("/");
          if (strings.length == 2) {
            final String project = strings[0];
            final String repository = strings[1].replace(".git", "");
            final String repositoryUrl = String.format("https://%s/p/%s/repositories/%s", spaceAddress, project, repository);

            return createResponse(repositoryUrl);
          }
        }
      }
    }

    return null;
  }

  @NotNull
  private Map<String, String> createResponse(@NotNull String url) {
    return ImmutableMap.of(
      PropertyType.CHANGE_SET_TYPE, url + "/revision/${changeSetId}",
      PropertyType.LINK_TEXT, "Open in Space",
      PropertyType.LINK_ICON_CLASS, "tc-icon_space"
    );
  }
}
