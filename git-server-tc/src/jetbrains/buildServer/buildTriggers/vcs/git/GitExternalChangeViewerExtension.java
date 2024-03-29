

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.serverSide.changeViewers.ExternalChangeViewerExtension;
import jetbrains.buildServer.serverSide.changeViewers.PropertyType;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class GitExternalChangeViewerExtension implements ExternalChangeViewerExtension {
  public GitExternalChangeViewerExtension(@NotNull ExtensionHolder extensionHolder) {
    extensionHolder.registerExtension(ExternalChangeViewerExtension.class, getClass().getName(), this);
  }

  @Nullable
  @Override
  public Map<String, String> getAvailableProperties(@NotNull final VcsRoot vcsRoot) {
    if (!Constants.VCS_NAME.equals(vcsRoot.getVcsName())) return null;
    String url = vcsRoot.getProperty(Constants.FETCH_URL);
    if (url == null) return null;

    URIish urIish;
    try {
      urIish = new URIish(url);
    } catch (URISyntaxException e) {
      return null;
    }

    VcsHostingRepo vcsHostingRepo = WellKnownHostingsUtil.getGitHubRepo(urIish);
    if (vcsHostingRepo != null) {
      final VcsHostingRepo finalVcsHostingRepo = vcsHostingRepo;
      return new HashMap<String, String>() {{
        put(PropertyType.CHANGE_SET_TYPE, finalVcsHostingRepo.repositoryUrl() + "/commit/${changeSetId}");
        put(PropertyType.LINK_TEXT, "Open in GitHub");
        put(PropertyType.LINK_ICON_CLASS, "tc-icon_github");
      }};
    }

    vcsHostingRepo = WellKnownHostingsUtil.getBitbucketRepo(urIish);
    if (vcsHostingRepo != null) {
      final VcsHostingRepo finalVcsHostingRepo = vcsHostingRepo;
      return new HashMap<String, String>() {{
        put(PropertyType.CHANGE_SET_TYPE, finalVcsHostingRepo.repositoryUrl() + "/commits/${changeSetId}");
        put(PropertyType.LINK_TEXT, "Open in Bitbucket Cloud");
        put(PropertyType.LINK_ICON_CLASS, "tc-icon_bitbucket");
      }};
    }

    vcsHostingRepo = WellKnownHostingsUtil.getGitlabRepo(urIish);
    if (vcsHostingRepo != null) {
      final VcsHostingRepo finalVcsHostingRepo = vcsHostingRepo;
      return new HashMap<String, String>() {{
        put(PropertyType.CHANGE_SET_TYPE, finalVcsHostingRepo.repositoryUrl() + "/commit/${changeSetId}");
        put(PropertyType.LINK_TEXT, "Open in Gitlab.com");
      }};
    }

    vcsHostingRepo = WellKnownHostingsUtil.getVSTSRepo(urIish);
    if (vcsHostingRepo != null) {
      final VcsHostingRepo finalVcsHostingRepo = vcsHostingRepo;
      return new HashMap<String, String>() {{
        put(PropertyType.CHANGE_SET_TYPE, finalVcsHostingRepo.repositoryUrl() + "/commit/${changeSetId}");
        put(PropertyType.LINK_TEXT, "Open in Azure DevOps Services");
        put(PropertyType.LINK_ICON_CLASS, "tc-icon_tfs");
      }};
    }

    vcsHostingRepo = WellKnownHostingsUtil.getBitbucketServerRepo(urIish);
    if (vcsHostingRepo != null) {
      final VcsHostingRepo finalVcsHostingRepo = vcsHostingRepo;
      return new HashMap<String, String>() {{
        put(PropertyType.CHANGE_SET_TYPE, finalVcsHostingRepo.repositoryUrl() + "/commits/${changeSetId}");
        put(PropertyType.LINK_TEXT, "Open in Bitbucket Server");
        put(PropertyType.LINK_ICON_CLASS, "tc-icon_bitbucket");
      }};
    }

    return null;
  }
}