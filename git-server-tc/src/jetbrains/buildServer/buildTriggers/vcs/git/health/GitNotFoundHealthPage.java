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
