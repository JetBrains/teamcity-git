/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.impl.BuildParametersMapImpl;
import jetbrains.buildServer.artifacts.ArtifactDependencyInfo;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.Option;
import jetbrains.buildServer.vcs.VcsChangeInfo;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.util.Util.map;

public class AgentRunningBuildBuilder {

  private Map<String, String> mySharedConfigParameters = new HashMap<String, String>();
  private Boolean myUseLocalMirrors;


  public static AgentRunningBuildBuilder runningBuild() {
    return new AgentRunningBuildBuilder();
  }


  public AgentRunningBuildBuilder sharedConfigParams(String... params) {
    mySharedConfigParameters.putAll(map(params));
    return this;
  }


  public AgentRunningBuildBuilder sharedConfigParams(Map<String, String> params) {
    mySharedConfigParameters.putAll(params);
    return this;
  }


  public AgentRunningBuildBuilder useLocalMirrors(boolean doUse) {
    mySharedConfigParameters.put(PluginConfigImpl.USE_MIRRORS, String.valueOf(doUse));
    return this;
  }


  public AgentRunningBuild build() {
    return new AgentRunningBuild() {
      @NotNull
      public File getBuildTempDirectory() {
        return new File(FileUtil.getTempDirectory());
      }

      @NotNull
      public BuildProgressLogger getBuildLogger() {
        return new NullBuildProgressLogger();
      }

      @NotNull
      public Map<String, String> getSharedConfigParameters() {
        return mySharedConfigParameters;
      }

      @NotNull
      public BuildParametersMap getMandatoryBuildParameters() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getCheckoutDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getWorkingDirectory() {
        throw new UnsupportedOperationException();
      }

      @Nullable
      public String getArtifactsPaths() {
        throw new UnsupportedOperationException();
      }

      public boolean getFailBuildOnExitCode() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public ResolvedParameters getResolvedParameters() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public String getRunType() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public UnresolvedParameters getUnresolvedParameters() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public BuildParametersMap getBuildParameters() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public Map<String, String> getRunnerParameters() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public String getBuildNumber() {
        throw new UnsupportedOperationException();
      }

      public void addSharedConfigParameter(final String key, final String value) {
        throw new UnsupportedOperationException();
      }

      public void addSharedSystemProperty(final String key, final String value) {
        throw new UnsupportedOperationException();
      }

      public void addSharedEnvironmentVariable(final String key, final String value) {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public BuildParametersMap getSharedBuildParameters() {
        return new BuildParametersMapImpl(new HashMap<String, String>());
      }

      @NotNull
      public ValueResolver getSharedParametersResolver() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public Collection<AgentBuildFeature> getBuildFeatures() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public Collection<AgentBuildFeature> getBuildFeaturesOfType(final String type) {
        throw new UnsupportedOperationException();
      }

      public void stopBuild(final String reason) {
        throw new UnsupportedOperationException();
      }

      @Nullable
      public BuildInterruptReason getInterruptReason() {
        throw new UnsupportedOperationException();
      }

      public boolean isBuildFailingOnServer() throws InterruptedException {
        throw new UnsupportedOperationException();
      }

      public boolean isInAlwaysExecutingStage() {
        throw new UnsupportedOperationException();
      }

      public String getProjectName() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public String getBuildTypeId() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public String getBuildTypeExternalId() {
        throw new UnsupportedOperationException();
      }

      public String getBuildTypeName() {
        throw new UnsupportedOperationException();
      }

      public long getBuildId() {
        throw new UnsupportedOperationException();
      }

      public boolean isCleanBuild() {
        throw new UnsupportedOperationException();
      }

      public boolean isPersonal() {
        throw new UnsupportedOperationException();
      }

      public boolean isPersonalPatchAvailable() {
        throw new UnsupportedOperationException();
      }

      public boolean isCheckoutOnAgent() {
        throw new UnsupportedOperationException();
      }

      public boolean isCheckoutOnServer() {
        throw new UnsupportedOperationException();
      }

      public long getExecutionTimeoutMinutes() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public List<ArtifactDependencyInfo> getArtifactDependencies() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public String getAccessUser() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public String getAccessCode() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public List<VcsRootEntry> getVcsRootEntries() {
        throw new UnsupportedOperationException();
      }

      public String getBuildCurrentVersion(final VcsRoot vcsRoot) {
        throw new UnsupportedOperationException();
      }

      public String getBuildPreviousVersion(final VcsRoot vcsRoot) {
        throw new UnsupportedOperationException();
      }

      public boolean isCustomCheckoutDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public List<VcsChangeInfo> getVcsChanges() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public List<VcsChangeInfo> getPersonalVcsChanges() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getAgentTempDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public BuildAgentConfiguration getAgentConfiguration() {
        throw new UnsupportedOperationException();
      }

      public <T> T getBuildTypeOptionValue(final Option<T> option) {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getDefaultCheckoutDirectory() {
        throw new UnsupportedOperationException();
      }

      @Nullable
      public AgentCheckoutMode getEffectiveCheckoutMode() {
        return null;
      }
    };
  };

}
