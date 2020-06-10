package jetbrains.buildServer.buildTriggers.vcs.git.health;

import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;

public class GitAgentVersionHealthPage extends HealthStatusItemPageExtension {

  public GitAgentVersionHealthPage(@NotNull PluginDescriptor pluginDescriptor,
                                   @NotNull final PagePlaces pagePlaces) {
    super(GitAgentVersionHealthReport.TYPE, pagePlaces);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("health/gitAgentVersionReport.jsp"));

    setVisibleOutsideAdminArea(false);
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    return super.isAvailable(request) && AuthUtil.canAdministerAgents(SessionUser.getUser(request));
  }
}
