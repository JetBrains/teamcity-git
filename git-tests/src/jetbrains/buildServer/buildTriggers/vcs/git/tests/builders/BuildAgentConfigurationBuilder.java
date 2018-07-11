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

package jetbrains.buildServer.buildTriggers.vcs.git.tests.builders;

import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.BuildAgentSystemInfo;
import jetbrains.buildServer.agent.BuildParametersMap;
import jetbrains.buildServer.parameters.ValueResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class BuildAgentConfigurationBuilder {

  private final File myAgentTempDir;
  private final File myAgentCacheDir;

  public BuildAgentConfigurationBuilder(@NotNull File agentTempDir, @NotNull File agentCacheDir) {
    myAgentTempDir = agentTempDir;
    myAgentCacheDir = agentCacheDir;
  }

  public static BuildAgentConfigurationBuilder agentConfiguration(@NotNull File agentTempDir, @NotNull File agentCacheDir) {
    return new BuildAgentConfigurationBuilder(agentTempDir, agentCacheDir);
  }

  @NotNull
  public BuildAgentConfiguration build() {
    return new BuildAgentConfiguration() {
      @NotNull
      public File getTempDirectory() {
        return myAgentTempDir;
      }

      @NotNull
      public File getAgentTempDirectory() {
        return new File(myAgentTempDir, "agent");
      }

      @NotNull
      public File getCacheDirectory(@NotNull final String key) {
        return myAgentCacheDir;
      }

      public int getOwnPort() {
        return 600;
      }

      @NotNull
      public Map<String, String> getConfigurationParameters() {
        return new HashMap<String, String>();
      }

      public String getName() {
        throw new UnsupportedOperationException();
      }

      public String getOwnAddress() {
        throw new UnsupportedOperationException();
      }

      public String getServerUrl() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public String getAuthorizationToken() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public String getPingCode() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getWorkDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getBuildTempDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getAgentToolsDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getSystemDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getAgentLibDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getAgentPluginsDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getAgentUpdateDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getAgentHomeDirectory() {
        return myAgentTempDir;
      }

      @NotNull
      public File getAgentLogsDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public BuildAgentSystemInfo getSystemInfo() {
        throw new UnsupportedOperationException();
      }

      public int getServerConnectionTimeout() {
        throw new UnsupportedOperationException();
      }

      public void addAlternativeAgentAddress(@NotNull final String address) {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public Map<String, String> getCustomProperties() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public Map<String, String> getAgentParameters() {
        throw new UnsupportedOperationException();
      }

      public void addCustomProperty(@NotNull final String name, @NotNull final String value) {
        throw new UnsupportedOperationException();
      }

      @Nullable
      public String getEnv(@NotNull final String key) {
        throw new UnsupportedOperationException();
      }

      public void addSystemProperty(@NotNull final String key, @NotNull final String value) {
        throw new UnsupportedOperationException();
      }

      public void addEnvironmentVariable(@NotNull final String key, @NotNull final String value) {
        throw new UnsupportedOperationException();
      }

      public void addConfigurationParameter(@NotNull final String key, @NotNull final String value) {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public BuildParametersMap getBuildParameters() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public ValueResolver getParametersResolver() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean removeConfigurationParameter(@NotNull final String key) {
        return false;
      }
    };
  }

}
