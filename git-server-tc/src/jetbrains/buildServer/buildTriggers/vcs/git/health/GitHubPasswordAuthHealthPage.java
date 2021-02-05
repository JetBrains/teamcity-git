package jetbrains.buildServer.buildTriggers.vcs.git.health;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;

public class GitHubPasswordAuthHealthPage extends HealthStatusItemPageExtension {
  public GitHubPasswordAuthHealthPage(@NotNull PluginDescriptor pluginDescriptor,
                                      @NotNull PagePlaces pagePlaces) {
    super(GitHubPasswordAuthHealthReport.REPORT_TYPE, pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("health/gitHubPasswordAuthReport.jsp"));
    setVisibleOutsideAdminArea(false);
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    if(!super.isAvailable(request)) return false;

    final Map<String,Object> additionalData = getStatusItem(request).getAdditionalData();
    final SVcsRoot vcsRoot = (SVcsRoot) additionalData.get(GitHubPasswordAuthHealthReport.VCS_ROOT_KEY);
    if (vcsRoot == null) return false;

    final SProject project = vcsRoot.getProject();
    return SessionUser.getUser(request).isPermissionGrantedForProject(project.getProjectId(), Permission.EDIT_PROJECT) &&
           project.getOwnVcsRoots().contains(vcsRoot); // check vcs root still exists
  }
}
