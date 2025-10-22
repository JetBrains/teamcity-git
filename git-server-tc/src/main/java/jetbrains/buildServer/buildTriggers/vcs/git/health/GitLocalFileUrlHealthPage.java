package jetbrains.buildServer.buildTriggers.vcs.git.health;

import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import org.jetbrains.annotations.NotNull;

public class GitLocalFileUrlHealthPage extends HealthStatusItemPageExtension {
  public GitLocalFileUrlHealthPage(@NotNull PluginDescriptor pluginDescriptor,
                                   @NotNull PagePlaces pagePlaces) {
    super(GitLocalFileUrlHealthReport.TYPE, pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("health/gitLocalFileUrlReport.jsp"));
    setVisibleOutsideAdminArea(false);
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    return super.isAvailable(request) && TeamCityProperties.getBoolean(Constants.WARN_FILE_URL);
  }
}
