

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

public class UnixScriptGen extends ScriptGen {

  private final EscapeEchoArgument myEscaper;

  public UnixScriptGen(@NotNull File tempDir,
                       @NotNull EscapeEchoArgument escaper) {
    super(tempDir);
    myEscaper = escaper;
  }

  @NotNull
  public File generateAskPass(@NotNull AuthSettings authSettings) throws IOException {
    return generateAskPass(authSettings.getPassword());
  }


  @NotNull
  @Override
  public File generateAskPass(@Nullable String password) throws IOException {
    File script = FileUtil.createTempFile(myTempDir, "pass", "", true);
    PrintWriter out = null;
    try {
      out = new PrintWriter(new FileWriter(script));
      out.println("#!/bin/sh");
      out.println("printf '%s' " + myEscaper.escape(password));
      if (!script.setExecutable(true))
        throw new IOException("Cannot make askpass script executable");
    } finally {
      if (out != null)
        out.close();
    }
    return script;
  }


  /**
   * #!/bin/sh
   * if [ "$1" = "erase" ]; then
   * rm '/.../credHelperXXXXXXX.sh';
   * exit;
   * fi
   * "/...../jre/bin/java" -cp '/...../plugins/jetbrains.git/lib/git-common.jar' jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.CredentialsHelper $*
   */
  @NotNull
  @Override
  public File generateCredentialHelper() throws IOException {
    File script = FileUtil.createTempFile(myTempDir, "credHelper", ".sh", true);
    try (PrintWriter out = new PrintWriter(script)) {
      out.println("#!/bin/sh");

      out.println("if [ \"$1\" = \"erase\" ]; then");
      out.printf("rm '%s';%n", script.getCanonicalPath());
      out.println("exit;");
      out.println("fi");

      out.printf("%s -cp '%s' %s %s $*%n",
                 getJavaPath(),
                 ClasspathUtil.composeClasspath(myClasses, null, null),
                 getLoggingSystemProperties(),
                 CredentialsHelper.class.getName()).flush();

      if (!script.setExecutable(true))
        throw new IOException("Cannot make credential helper script executable");
    }

    return script;
  }
}