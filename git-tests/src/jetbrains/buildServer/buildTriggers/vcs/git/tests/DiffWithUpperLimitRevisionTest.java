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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.FlowLogger;
import jetbrains.buildServer.agent.NullBuildProgressLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.messages.BuildMessage1;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.*;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitVersionProvider.getGitPath;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder.runningBuild;
import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class DiffWithUpperLimitRevisionTest extends BaseRemoteRepositoryTest {

  private GitAgentVcsSupport myVcsSupport;
  private File myCheckoutDir;
  private VcsRoot myRoot;
  private BuildLogger myBuildLogger;

  public DiffWithUpperLimitRevisionTest() {
    super("repo.git");
  }


  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myCheckoutDir = myTempFiles.createTempDir();
    myVcsSupport = new AgentSupportBuilder(myTempFiles).build();
    String pathToGit = getGitPath();
    myRoot = vcsRoot()
      .withAgentGitPath(pathToGit)
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("master")
      .build();
    myBuildLogger = new BuildLogger();
  }


  public void no_error_if_upper_limit_revision_param_is_missing() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = createBuild(version, null);
    myVcsSupport.updateSources(myRoot, new CheckoutRules("+:dir"), version, myCheckoutDir, build, false);
  }


  public void error_if_diff_found() throws Exception {
    String version = "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7";
    AgentRunningBuild build = createBuild(version, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9");
    myVcsSupport.updateSources(myRoot, new CheckoutRules("+:dir"), version, myCheckoutDir, build, false);
    then(myBuildLogger.getErrors()).isNotEmpty();
    then(myBuildLogger.getErrors().iterator().next()).contains("Files matched by checkout rules changed between build revision and upper-limit revision");
  }


  public void can_be_disabled() throws Exception {
    String version = "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7";
    AgentRunningBuild build = createBuild(version, "465ad9f630e451b9f2b782ffb09804c6a98c4bb9", "teamcity.git.checkDiffWithUpperLimitRevision", "false");
    myVcsSupport.updateSources(myRoot, new CheckoutRules("+:dir"), version, myCheckoutDir, build, false);
    then(myBuildLogger.getErrors()).isEmpty();
  }


  private AgentRunningBuild createBuild(@NotNull String buildRevision, @Nullable String upperLimitRevision, String... additionalParams) {
    String rootExtId = "RootExtId";
    Map<String, String> params = new HashMap<>();
    params.put(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true");
    params.put("build.vcs.number." + rootExtId, buildRevision);
    params.put("build.vcs.number.1", buildRevision);
    if (upperLimitRevision != null) {
      params.put("teamcity.upperLimitRevision." + rootExtId, upperLimitRevision);
    }
    params.putAll(map(additionalParams));
    return runningBuild().sharedConfigParams(params).withBuildLogger(myBuildLogger).build();
  }


  private class BuildLogger extends NullBuildProgressLogger {
    private List<String> myErrors = new ArrayList<>();

    @NotNull
    List<String> getErrors() {
      return myErrors;
    }

    @Override
    public void error(final String message) {
      myErrors.add(message);
    }
  }
}
