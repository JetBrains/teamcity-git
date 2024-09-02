package jetbrains.buildServer.buildTriggers.vcs.git.health;

import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRepoOperations;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitServerVersionHealthPage extends HealthStatusItemPageExtension {

  private final GitRepoOperations myGitOperations;


  public GitServerVersionHealthPage(@NotNull PluginDescriptor pluginDescriptor,
                                    @NotNull final PagePlaces pagePlaces,
                                    @NotNull GitRepoOperations gitOperations) {
    super(GitServerVersionHealthReport.TYPE, pagePlaces);
    myGitOperations = gitOperations;

    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("health/gitServerVersionReport.jsp"));

    setVisibleOutsideAdminArea(false);
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    if (!super.isAvailable(request)) return false;
    if (!SessionUser.getUser(request).isPermissionGrantedGlobally(Permission.MANAGE_SERVER_INSTALLATION)) return false;
    if (!myGitOperations.isNativeGitOperationsEnabled()) return false;

    final HealthStatusItem item = getStatusItem(request);
    final Object gitExec = item.getAdditionalData().get("gitExec");

    return gitExec == null || gitExec instanceof GitExec && ((GitExec)gitExec).getVersion().equals(getCurrentGitVersion());
  }

  @Nullable
  private GitVersion getCurrentGitVersion() {
    try {
      return myGitOperations.detectGit().getVersion();
    } catch (VcsException e) {
      return null;
    }
  }
}
