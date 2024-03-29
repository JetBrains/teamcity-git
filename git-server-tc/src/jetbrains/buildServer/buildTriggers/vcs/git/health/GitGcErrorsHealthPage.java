

package jetbrains.buildServer.buildTriggers.vcs.git.health;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.serverSide.NodeSpecificConfigs;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.serverSide.impl.ErrorMessageSanitizer;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;

public class GitGcErrorsHealthPage extends HealthStatusItemPageExtension {

  private static final Logger LOG = Logger.getInstance(GitGcErrorsHealthPage.class.getName());
  private final ServerPaths myServerPaths;
  private final MirrorManager myMirrorManager;

  public GitGcErrorsHealthPage(@NotNull PluginDescriptor pluginDescriptor,
                               @NotNull PagePlaces pagePlaces,
                               @NotNull ServerPaths serverPaths,
                               @NotNull MirrorManager mirrorManager) {
    super(GitGcErrorsHealthReport.REPORT_TYPE, pagePlaces);
    myServerPaths = serverPaths;
    myMirrorManager = mirrorManager;
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("health/gitGcErrorsReport.jsp"));
    setVisibleOutsideAdminArea(false);
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    if (!super.isAvailable(request))
      return false;
    if (!SessionUser.getUser(request).isPermissionGrantedGlobally(Permission.MANAGE_SERVER_INSTALLATION)) return false;
    HealthStatusItem item = getStatusItem(request);
    Object path = item.getAdditionalData().get(GitGcErrorsHealthReport.ERRORS_KEY);
    return path instanceof Map && !((Map) path).isEmpty();
  }

  @Override
  public void fillModel(@NotNull final Map<String, Object> model, @NotNull final HttpServletRequest request) {

    HealthStatusItem item = getStatusItem(request);
    Object errors = item.getAdditionalData().get(GitGcErrorsHealthReport.ERRORS_KEY);
    Map<String, Pair<String, String>> sortedErrors = new TreeMap<>();
    if (errors instanceof Map) {
      File mainDataDirectory;
      try {
        mainDataDirectory = myServerPaths.getDataDirectory().getCanonicalFile();
      } catch (IOException e) {
        mainDataDirectory = myServerPaths.getDataDirectory().getAbsoluteFile();
      }

      ErrorMessageSanitizer errorMessageSanitizer = new ErrorMessageSanitizer(mainDataDirectory.getAbsolutePath(), "<TeamCity Data Directory>");
      File currentNodeDataDir = NodeSpecificConfigs.getNodeDataDirectory(mainDataDirectory);
      errorMessageSanitizer.addSanitizedPath(currentNodeDataDir.getAbsolutePath(), "<Node Data Directory>");

      //noinspection unchecked
      Set<Map.Entry> entries = ((Map)errors).entrySet();
      for (Map.Entry entry : entries) {
        Object key = entry.getKey();
        Object value = entry.getValue();
        if (key instanceof File && value instanceof String) {
          try {
            String url = myMirrorManager.getUrl(((File)key).getName());
            if (url != null) {
              sortedErrors.put(url, Pair.create(errorMessageSanitizer.sanitize(((File)key).getCanonicalPath()), (String) value));
            }
          } catch (IOException e) {
            LOG.warnAndDebugDetails("Error while preparing health report data", e);
          }
        }
      }
    }
    model.put("errors", sortedErrors);
  }
}