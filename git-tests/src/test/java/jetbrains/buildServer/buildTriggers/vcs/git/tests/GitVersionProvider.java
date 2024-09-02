

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.intellij.openapi.util.text.StringUtil;
import java.lang.reflect.Method;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitFacadeImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.DataProvider;

public class GitVersionProvider {

  @DataProvider
  public static Object[][] version(Method testMethod) throws Exception {
    String gitPath = getGitPath();
    GitVersion version = new AgentGitFacadeImpl(gitPath).version().call();
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