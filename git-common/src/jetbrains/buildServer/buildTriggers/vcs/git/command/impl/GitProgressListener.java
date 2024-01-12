

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import jetbrains.buildServer.buildTriggers.vcs.git.GitProgressLogger;
import org.jetbrains.annotations.NotNull;

public class GitProgressListener implements LineAwareByteArrayOutputStream.LineListener {
  private final GitProgressLogger myLogger;

  public GitProgressListener(@NotNull GitProgressLogger logger) {
    myLogger = logger;
  }

  public void newLineDetected(@NotNull String line) {
    myLogger.progressMessage(line);
  }
}