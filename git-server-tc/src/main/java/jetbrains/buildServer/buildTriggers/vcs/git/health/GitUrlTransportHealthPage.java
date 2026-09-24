package jetbrains.buildServer.buildTriggers.vcs.git.health;

import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import org.jetbrains.annotations.NotNull;

public class GitUrlTransportHealthPage extends HealthStatusItemPageExtension {
  public GitUrlTransportHealthPage(@NotNull PluginDescriptor pluginDescriptor,
                                   @NotNull PagePlaces pagePlaces) {
    super(GitUrlTransportHealthReport.TYPE, pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("health/gitUrlTransportReport.jsp"));
    setVisibleOutsideAdminArea(false);
    register();
  }
}
