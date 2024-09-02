

package jetbrains.buildServer.buildTriggers.vcs.git.command.credentials;

import java.io.*;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ScriptGen {

  protected final File myTempDir;

  public ScriptGen(@NotNull File tempDir) {
    myTempDir = tempDir;
  }

  @NotNull
  public abstract File generateAskPass(@NotNull AuthSettings authSettings) throws IOException;

  @NotNull
  public abstract File generateAskPass(@Nullable String password) throws IOException;

  @NotNull
  public abstract File generateCredentialHelper() throws IOException;

  protected String getJavaPath() {
    String javaHome = System.getProperty("java.home");
    if (StringUtil.isNotEmpty(javaHome)) {
      return "\"" + javaHome + File.separatorChar + "bin" + File.separatorChar + "java\"";
    }
    return "java";
  }
}