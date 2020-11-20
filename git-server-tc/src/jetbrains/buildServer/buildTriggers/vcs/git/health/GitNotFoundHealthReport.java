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

import jetbrains.buildServer.buildTriggers.vcs.git.Cleanup;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GitNotFoundHealthReport extends HealthStatusReport {

  private static final String PREFIX = "gitNotFound";
  static final String REPORT_TYPE = PREFIX + "HealthReport";
  private static final ItemCategory CATEGORY =
    new ItemCategory(PREFIX + "HealthCategory", "Git executable not found", ItemSeverity.WARN);
  static final String PATH_KEY = "path";
  static final String ERROR_KEY = "error";

  private final Cleanup myCleanup;


  public GitNotFoundHealthReport(@NotNull Cleanup cleanup) {
    myCleanup = cleanup;
  }

  @NotNull
  @Override
  public String getType() {
    return REPORT_TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Git executable not found";
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Collections.singletonList(CATEGORY);
  }

  @Override
  public boolean canReportItemsFor(@NotNull final HealthStatusScope scope) {
    return scope.globalItems() && scope.isItemWithSeverityAccepted(ItemSeverity.WARN);
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer resultConsumer) {
    Cleanup.RunGitError error = myCleanup.getNativeGitError();
    if (error != null) {
      Map<String, Object> data = new HashMap<>();
      data.put(PATH_KEY, error.getGitPath());
      data.put(ERROR_KEY, error.getError());
      resultConsumer.consumeGlobal(new HealthStatusItem(PREFIX + "HealthItemId", CATEGORY, ItemSeverity.WARN, data));
    }
  }
}
