package jetbrains.buildServer.buildTriggers.vcs.git.health;

import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRepoOperations;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;

public class GitServerLanguageHealthPage extends HealthStatusItemPageExtension {

  private final GitRepoOperations myGitOperations;

  public GitServerLanguageHealthPage(@NotNull PluginDescriptor pluginDescriptor,
                                     @NotNull final PagePlaces pagePlaces,
                                     @NotNull GitRepoOperations gitOperations) {
    super(GitServerLanguageHealthReport.TYPE, pagePlaces);
    myGitOperations = gitOperations;
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("health/gitServerLanguageReport.jsp"));
    setVisibleOutsideAdminArea(false);
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    if (!super.isAvailable(request)) return false;
    if (!SessionUser.getUser(request).isPermissionGrantedGlobally(Permission.MANAGE_SERVER_INSTALLATION)) return false;

    return myGitOperations.isNativeGitOperationsEnabled();
  }
}
