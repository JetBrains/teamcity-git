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

package jetbrains.buildServer.buildTriggers.vcs.git.tests.command;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelperImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.NoBuildContext;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.FetchCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitIndexCorruptedException;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.fail;

@Test
public class FetchCommandImplTest {

  @BeforeMethod
  public void setUp() throws Exception {
    new TeamCityProperties() {{setModel(new BasePropertiesModel() {});}};
  }

  @TestFor(issues = "TW-18853")
  public void should_throw_special_exception_when_stderr_mentions_broken_index() throws VcsException {
    ScriptGen fakeGen = new ScriptGen(new File(".")) {
      @NotNull
      public File generateAskPass(@NotNull AuthSettings authSettings) throws IOException {
        return new File(".");
      }
      @NotNull
      @Override
      public File generateAskPass(@NotNull final String password) throws IOException {
        return new File(".");
      }
      @NotNull
      protected String getCredHelperTemplate() {
        return "";
      }
    };

    AgentGitCommandLine failedCmd = new AgentGitCommandLine(null, fakeGen, new NoBuildContext()) {
      @Override
      public ExecResult run(@NotNull GitCommandSettings settings) throws VcsException {
        throw new VcsException("fatal: index file smaller than expected");
      }
    };

    FetchCommand fetch = new FetchCommandImpl(failedCmd)
      .setRefspec("+refs/heads/*:refs/remotes/origin/*")
      .setTimeout(3600)
      .setAuthSettings(new AuthSettings(new HashMap<String, String>(), new URIishHelperImpl()));

    try {
      fetch.call();
    } catch (GitIndexCorruptedException e) {
      //expected
    } catch (VcsException e) {
      fail("GitIndexCorruptedException should be thrown");
    }
  }

}
