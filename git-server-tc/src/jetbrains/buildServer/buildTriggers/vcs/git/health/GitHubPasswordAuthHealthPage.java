package jetbrains.buildServer.buildTriggers.vcs.git.health;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitHubPasswordAuthRootRegistryFactory;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitHubPasswordAuthHealthPage extends HealthStatusItemPageExtension {
  public GitHubPasswordAuthHealthPage(@NotNull PluginDescriptor pluginDescriptor,
                                      @NotNull PagePlaces pagePlaces) {
    super(GitHubPasswordAuthRootRegistryFactory.REPORT_TYPE, pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("health/gitHubPasswordAuthReport.jsp"));
    setVisibleOutsideAdminArea(false);
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    if(!super.isAvailable(request)) return false;

    final SVcsRoot vcsRoot = getRootFromRequest(request);
    if (vcsRoot == null) return false;

    final SProject project = vcsRoot.getProject();
    return SessionUser.getUser(request).isPermissionGrantedForProject(project.getProjectId(), Permission.EDIT_PROJECT) &&
           project.getOwnVcsRoots().contains(vcsRoot); // check vcs root still exists
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);

    final SVcsRoot vcsRoot = getRootFromRequest(request);
    if (vcsRoot == null) return;

    final String passwordProperty = vcsRoot.getProperty(Constants.PASSWORD);
    model.put("isPasswordContainsReference", StringUtil.isNotEmpty(passwordProperty) && ReferencesResolverUtil.containsReference(passwordProperty));
  }

  @Nullable
  private SVcsRoot getRootFromRequest(@NotNull HttpServletRequest request) {
    final Map<String,Object> additionalData = getStatusItem(request).getAdditionalData();
    return  (SVcsRoot) additionalData.get(GitHubPasswordAuthHealthReport.VCS_ROOT_KEY);
  }
}
