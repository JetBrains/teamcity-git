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

package jetbrains.buildServer.buildTriggers.vcs.git.patch;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.ChangeType;
import jetbrains.buildServer.vcs.CheckoutRules;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author dmitry.neverov
*/
public final class BuildPatchLogger {
  private final Logger myDelegate;
  private final String myRepoDebugInfo;
  private final boolean myVerboseTreeWalkLog;

  public BuildPatchLogger(@NotNull Logger delegate, @NotNull String repositoryDebugInfo, boolean verboseTreeWalkLog) {
    myDelegate = delegate;
    myRepoDebugInfo = repositoryDebugInfo;
    myVerboseTreeWalkLog = verboseTreeWalkLog;
  }

  public void logBuildCleanPatch(@NotNull String toRevision) {
    if (myDelegate.isDebugEnabled())
      myDelegate.debug("Creating clean patch " + toRevision + " for " + myRepoDebugInfo);
  }

  public void logBuildIncrementalPatch(@NotNull String fromRevision, @NotNull String toRevision) {
    if (myDelegate.isDebugEnabled())
      myDelegate.debug("Creating patch " + fromRevision + ".." + toRevision + " for " + myRepoDebugInfo);
  }

  public void logFromRevisionNotFound(@NotNull String fromRevision) {
    myDelegate.info("The commit " + fromRevision + " is not available in the repository, build a full patch");
  }

  public void logFileExcludedByCheckoutRules(@NotNull String path, @NotNull CheckoutRules rules) {
    myDelegate.debug("File " + path + " excluded by checkout rules " + rules.getAsString());
  }

  public void logVisitFile(@NotNull String fileInfo) {
    if (myDelegate.isDebugEnabled() && myVerboseTreeWalkLog)
      myDelegate.debug("Visit file " + fileInfo + " in " + myRepoDebugInfo);
  }

  public void logChangeType(@NotNull String path, @NotNull ChangeType changeType) {
    if (myDelegate.isDebugEnabled() && myVerboseTreeWalkLog)
      myDelegate.debug("Change type of " + path + ": " + changeType);
  }

  public void logAddFile(@NotNull String mappedPath, long contentSize) {
    myDelegate.debug("Add file " + mappedPath + ", size " + contentSize + " bytes");
  }

  public void logFileModeChanged(@NotNull String modeDiff, @NotNull String fileInfo) {
    if (myDelegate.isDebugEnabled() && myVerboseTreeWalkLog)
      myDelegate.debug("The mode change " + modeDiff + " is detected for " + fileInfo);
  }

  public void cannotLoadFile(@Nullable String path, @NotNull ObjectId objectId) {
    myDelegate.error("Unable to load file: " + path + "(" + objectId.name() + ") from: " + myRepoDebugInfo);
  }

  public boolean isDebugEnabled() {
    return myDelegate.isDebugEnabled();
  }
}
