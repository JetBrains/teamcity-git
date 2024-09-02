

package jetbrains.buildServer.buildTriggers.vcs.git.health;

import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;

public class GitNotFoundHealthPage extends HealthStatusItemPageExtension {

  public GitNotFoundHealthPage(@NotNull PluginDescriptor pluginDescriptor,
                               @NotNull PagePlaces pagePlaces) {
    super(GitNotFoundHealthReport.REPORT_TYPE, pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("health/gitNotFoundReport.jsp"));
    setVisibleOutsideAdminArea(false);
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    if (!super.isAvailable(request))
      return false;
    if (!SessionUser.getUser(request).isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS)) return false;
    HealthStatusItem item = getStatusItem(request);
    Object path = item.getAdditionalData().get(GitNotFoundHealthReport.PATH_KEY);
    Object error = item.getAdditionalData().get(GitNotFoundHealthReport.ERROR_KEY);
    return path instanceof String && error instanceof VcsException;
  }
}