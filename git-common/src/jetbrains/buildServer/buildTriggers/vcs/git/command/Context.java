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

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.GitProgressLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Context {

  @Nullable
  String getInterruptionReason();

  @Nullable
  String getSshMacType();

  @Nullable
  String getPreferredSshAuthMethods();

  boolean isProvideCredHelper();

  @Nullable
  Charset getCharset();

  boolean isDebugSsh();

  boolean isDeleteTempFiles();

  boolean isUseGitSshCommand();

  @NotNull
  File getTempDir();

  @NotNull
  Map<String, String> getEnv();

  @NotNull
  GitExec getGitExec();

  @NotNull
  GitVersion getGitVersion();

  int getIdleTimeoutSeconds();

  @Nullable
  String getSshRequestToken();

  @NotNull
  Collection<String> getCustomConfig();

  @NotNull
  GitProgressLogger getLogger();

  boolean isDebugGitCommands();

  @NotNull
  List<String> getKnownRepoLocations();

  boolean isUseSshAskPass();

  @Nullable
  String getSshCommandOptions();
}
