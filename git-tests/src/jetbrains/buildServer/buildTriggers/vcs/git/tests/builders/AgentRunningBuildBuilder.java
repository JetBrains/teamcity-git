

package jetbrains.buildServer.buildTriggers.vcs.git.tests.builders;

import java.io.File;
import java.util.*;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.impl.BuildNodeIdHolder;
import jetbrains.buildServer.agent.impl.BuildParametersMapImpl;
import jetbrains.buildServer.agentServer.AgentBuild;
import jetbrains.buildServer.artifacts.ArtifactDependencyInfo;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.Option;
import jetbrains.buildServer.util.PasswordReplacer;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsChangeInfo;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import jetbrains.buildServer.xmlrpc.NodeIdHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.util.Util.map;

public class AgentRunningBuildBuilder {

  private Map<String, String> mySharedConfigParameters = new HashMap<String, String>();
  private Map<String, String> mySharedBuildParameters = new HashMap<String, String>();
  private List<VcsRootEntry> myRootEntries = null;
  private BuildProgressLogger myBuildLogger = null;
  private BuildAgentConfiguration myConfiguration;
  private File myCheckoutDir;

  public static AgentRunningBuildBuilder runningBuild() {
    return new AgentRunningBuildBuilder();
  }

  public AgentRunningBuildBuilder withBuildLogger(BuildProgressLogger logger) {
    myBuildLogger = logger;
    return this;
  }

  public AgentRunningBuildBuilder sharedConfigParams(String... params) {
    mySharedConfigParameters.putAll(map(params));
    return this;
  }


  public AgentRunningBuildBuilder sharedConfigParams(Map<String, String> params) {
    mySharedConfigParameters.putAll(params);
    return this;
  }


  public AgentRunningBuildBuilder sharedEnvVariable(String key, String value) {
    mySharedBuildParameters.put("env." + key, value);
    return this;
  }


  public AgentRunningBuildBuilder useLocalMirrors(boolean doUse) {
    mySharedConfigParameters.put(PluginConfigImpl.USE_MIRRORS, String.valueOf(doUse));
    return this;
  }


  public AgentRunningBuildBuilder addRootEntry(@NotNull VcsRoot root, @NotNull String rules) {
    if (myRootEntries == null) {
      myRootEntries = new ArrayList<>();
    }
    myRootEntries.add(new VcsRootEntry(root, new CheckoutRules(rules)));
    return this;
  }

  public AgentRunningBuildBuilder addRoot(@NotNull VcsRoot root) {
    if (myRootEntries == null) {
      myRootEntries = new ArrayList<>();
    }
    myRootEntries.add(new VcsRootEntry(root, CheckoutRules.DEFAULT));
    return this;
  }

  public AgentRunningBuildBuilder withAgentConfiguration(@NotNull final BuildAgentConfiguration configuration) {
    myConfiguration = configuration;
    return this;
  }

  public AgentRunningBuildBuilder withCheckoutDir(@NotNull final File checkoutDir) {
    myCheckoutDir = checkoutDir;
    return this;
  }


  public AgentRunningBuild build() {
    return new AgentRunningBuild() {
      @NotNull
      public File getBuildTempDirectory() {
        return new File(FileUtil.getTempDirectory());
      }

      @Override
      public void interruptBuild(@NotNull final String comment, final boolean reQueue) {

      }

      @NotNull
      public BuildProgressLogger getBuildLogger() {
        return myBuildLogger != null ? myBuildLogger : new NullBuildProgressLogger();
      }

      @NotNull
      public Map<String, String> getSharedConfigParameters() {
        return mySharedConfigParameters;
      }

      @NotNull
      public File getCheckoutDirectory() {
        if (myCheckoutDir == null) throw new UnsupportedOperationException();
        return myCheckoutDir;
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
      public BuildParametersMap getBuildParameters() {
        return new BuildParametersMap() {
          @NotNull
          public Map<String, String> getEnvironmentVariables() {
            return new HashMap<String, String>(0);
          }

          @NotNull
          public Map<String, String> getSystemProperties() {
            return new HashMap<String, String>(0);
          }

          @NotNull
          public Map<String, String> getAllParameters() {
            return new HashMap<String, String>(0);
          }
        };
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
        return new BuildParametersMapImpl(mySharedBuildParameters);
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
        return null;
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
        return 1;
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
        return myRootEntries == null ? Collections.emptyList() : myRootEntries;
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
        return new File(FileUtil.getTempDirectory());
      }

      @NotNull
      public BuildAgentConfiguration getAgentConfiguration() {
        return myConfiguration;
      }

      public <T> T getBuildTypeOptionValue(final Option<T> option) {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public File getDefaultCheckoutDirectory() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public String getVcsSettingsHashForCheckoutMode(AgentCheckoutMode agentCheckoutMode) {throw new UnsupportedOperationException();}

      @Nullable
      public AgentCheckoutMode getEffectiveCheckoutMode() {
        return null;
      }

      @NotNull
      public AgentBuild.CheckoutType getCheckoutType() {
        return AgentBuild.CheckoutType.ON_AGENT;
      }

      @NotNull
      public PasswordReplacer getPasswordReplacer() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      @Override
      public String describe(final boolean verbose) { return "no details";}

      @NotNull
      @Override
      public Map<String, String> getArtifactStorageSettings() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public NodeIdHolder getNodeIdHolder() {
        return new BuildNodeIdHolder(this, "main");
      }

      @NotNull
      @Override
      public List<BuildRunnerSettings> getBuildRunners() {
        throw new UnsupportedOperationException();
      }
    };
  };

}