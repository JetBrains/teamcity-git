/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.health;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
    if (!SessionUser.getUser(request).isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS)) return false;
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
      String baseDir;
      try {
        baseDir = myServerPaths.getDataDirectory().getCanonicalPath();
      } catch (IOException e) {
        baseDir = myServerPaths.getDataDirectory().getAbsolutePath();
      }

      //noinspection unchecked
      Set<Map.Entry> entries = ((Map)errors).entrySet();
      for (Map.Entry entry : entries) {
        Object key = entry.getKey();
        Object value = entry.getValue();
        if (key instanceof File && value instanceof String) {
          try {
            String relativePath = FileUtil.getRelativePath(baseDir, ((File)key).getCanonicalPath(), File.separatorChar);
            String url = myMirrorManager.getUrl(((File)key).getName());
            if (url != null) {
              sortedErrors.put(url, Pair.create("<TeamCity data dir>" + File.separatorChar + relativePath, (String) value));
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
