

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import jetbrains.buildServer.agent.ClasspathUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.CredentialsHelper;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WinScriptGen extends ScriptGen {

  private final EscapeEchoArgument myEscaper;

  public WinScriptGen(@NotNull File tempDir,
                      @NotNull EscapeEchoArgument escaper) {
    super(tempDir);
    myEscaper = escaper;
  }

  @NotNull
  public File generateAskPass(@NotNull AuthSettings authSettings) throws IOException {
    return generateAskPass(authSettings.getPassword());
  }

  @NotNull
  public File generateAskPass(@Nullable String password) throws IOException {
    File script = FileUtil.createTempFile(myTempDir, "pass", ".bat", true);
    PrintWriter out = null;
    try {
      out = new PrintWriter(new FileWriter(script));
      out.println("@echo " + myEscaper.escape(password));
      if (!script.setExecutable(true))
        throw new IOException("Cannot make askpass script executable");
    } finally {
      if (out != null)
        out.close();
    }
    return script;
  }


  /**
   * @echo off
   * if ""%1"" == ""erase"" goto erase
   * "C:\...\bin\java" -cp "C:/.../TeamCity/buildAgent/plugins/jetbrains.git/lib/git-common.jar" jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.CredentialsHelper %*
   * goto end
   * :erase
   * del "C:\...\credHelperXXXXXXXXXX.bat"
   * :end
   *
   */
  @NotNull
  @Override
  public File generateCredentialHelper() throws IOException {
    File script = FileUtil.createTempFile(myTempDir, "cred", ".bat", true);
    try (PrintWriter out = new PrintWriter(script)) {
      out.println("@echo off");
      out.println("if \"\"%1\"\" == \"\"erase\"\" goto erase");
      out.printf("%s -cp \"%s\" %s %%*%n",
                 getJavaPath(),
                 ClasspathUtil.composeClasspath(new Class[]{CredentialsHelper.class}, null, null),
                 CredentialsHelper.class.getName()).flush();
      out.println("goto end");
      out.println(":erase");
      out.printf("echo \"%s\"%n", script.getAbsolutePath());
      out.printf("echo \"%s\"%n", script.getCanonicalPath());
      out.println("echo 123");
      out.println(":end");

      if (!script.setExecutable(true))
        throw new IOException("Cannot make credential helper script executable");
    }
    return script;
  }
}