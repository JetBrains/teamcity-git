package jetbrains.buildServer.buildTriggers.vcs.git.health;

import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import org.jetbrains.annotations.NotNull;

public class GitDisallowedTransportUrlHealthPage extends HealthStatusItemPageExtension {
  public GitDisallowedTransportUrlHealthPage(@NotNull PluginDescriptor pluginDescriptor,
                                             @NotNull PagePlaces pagePlaces) {
    super(GitDisallowedTransportUrlHealthReport.TYPE, pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("health/gitDisallowedTransportUrlReport.jsp"));
    setVisibleOutsideAdminArea(false);
    register();
  }
}
