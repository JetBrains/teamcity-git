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

import com.intellij.openapi.util.text.StringUtil;
import java.lang.reflect.Method;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitProgressLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.NativeGitFacade;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.DataProvider;

public class GitVersionProvider {

  @DataProvider
  public static Object[][] version(Method testMethod) throws Exception {
    String gitPath = getGitPath();
    GitVersion version = new NativeGitFacade(gitPath, GitProgressLogger.NO_OP).version().call();
    RequiredGitVersion requirement = testMethod.getAnnotation(RequiredGitVersion.class);
    if (requirement == null)
      requirement = testMethod.getDeclaringClass().getAnnotation(RequiredGitVersion.class);
    if (requirement == null) {
      return new Object[][]{new Object[]{new GitExec(gitPath, version)}};
    } else {
      GitVersion minRequired = GitVersion.parse("git version " + requirement.min());
      if (version.isLessThan(minRequired)) {
        return new Object[0][];
      } else {
        return new Object[][]{new Object[]{new GitExec(gitPath, version)}};
      }
    }
  }


  @NotNull
  static String getGitPath() {
    String path = System.getenv(Constants.TEAMCITY_AGENT_GIT_PATH);
    if (!StringUtil.isEmpty(path)) {
      return path;
    } else {
      return "git";
    }
  }
}
