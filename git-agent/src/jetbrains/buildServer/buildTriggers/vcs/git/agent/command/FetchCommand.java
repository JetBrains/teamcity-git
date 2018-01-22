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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public interface FetchCommand extends BaseCommand {

  @NotNull
  FetchCommand setUseNativeSsh(boolean useNativeSsh);

  @NotNull
  FetchCommand setTimeout(int timeout);

  @NotNull
  FetchCommand setRefspec(@NotNull String refspec);

  @NotNull
  FetchCommand setQuite(boolean quite);

  @NotNull
  FetchCommand setShowProgress(boolean showProgress);

  @NotNull
  FetchCommand setAuthSettings(@NotNull AuthSettings settings);

  @NotNull
  FetchCommand setDepth(int depth);

  @NotNull
  FetchCommand setFetchTags(boolean fetchTags);

  void call() throws VcsException;

}
