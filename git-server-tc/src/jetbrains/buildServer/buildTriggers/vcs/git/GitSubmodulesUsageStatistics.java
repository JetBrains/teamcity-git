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

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.usageStatistics.impl.providers.BaseVCSFeatureUsageStatisticsProvider;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsManager;
import org.jetbrains.annotations.NotNull;

public class GitSubmodulesUsageStatistics extends BaseVCSFeatureUsageStatisticsProvider {


  public GitSubmodulesUsageStatistics(@NotNull VcsManager vcsManager) {
    super(vcsManager);
  }

  @NotNull
  @Override
  protected String getFeatureName() {
    return "subRepoSupport-git";
  }

  @NotNull
  @Override
  protected String getFeatureDisplayName() {
    return "Git VCS roots with submodules support enabled";
  }

  @Override
  protected boolean hasFeature(@NotNull final SVcsRoot root) {
    String submoduleCheckout = root.getProperty(Constants.SUBMODULES_CHECKOUT);
    if (submoduleCheckout == null)
      return false;
    return !submoduleCheckout.equals(SubmodulesCheckoutPolicy.IGNORE.name());
  }
}
