

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
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

  @NotNull
  @Override
  public File generateCredentialHelper() throws IOException {
    //todo write for windows
    File script = FileUtil.createTempFile(myTempDir, "credHelper", ".bat", true);
    return script;
  }
}