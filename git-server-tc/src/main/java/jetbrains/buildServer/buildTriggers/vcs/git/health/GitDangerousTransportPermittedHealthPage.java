package jetbrains.buildServer.buildTriggers.vcs.git.health;

import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import org.jetbrains.annotations.NotNull;

public class GitDangerousTransportPermittedHealthPage extends HealthStatusItemPageExtension {
  public GitDangerousTransportPermittedHealthPage(@NotNull PluginDescriptor pluginDescriptor,
                                                  @NotNull PagePlaces pagePlaces) {
    super(GitDangerousTransportPermittedHealthReport.TYPE, pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("health/gitDangerousTransportPermittedReport.jsp"));
    setVisibleOutsideAdminArea(false);
    register();
  }
}
