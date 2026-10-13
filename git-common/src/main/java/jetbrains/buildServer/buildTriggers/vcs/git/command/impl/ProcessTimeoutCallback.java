

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import org.jetbrains.annotations.Nullable;

public class ProcessTimeoutCallback extends SimpleCommandLineProcessRunner.ProcessRunCallbackAdapter {

  @Nullable private final Integer myTimeoutSeconds;
  @Nullable private final Integer myMaxAcceptedOutputSize;

  public ProcessTimeoutCallback(int timeoutSeconds) {
    this(timeoutSeconds, null);
  }

  public ProcessTimeoutCallback(@Nullable final Integer timeoutSeconds, @Nullable final Integer maxAcceptedOutputSize) {
    myTimeoutSeconds = timeoutSeconds;
    myMaxAcceptedOutputSize = maxAcceptedOutputSize;
  }

  @Nullable
  @Override
  public Integer getOutputIdleSecondsTimeout() {
    return myTimeoutSeconds;
  }

  @Nullable
  @Override
  public Integer getMaxAcceptedOutputSize() {
    return myMaxAcceptedOutputSize;
  }

  @Override
  public boolean terminateEntireProcessTree() {
    // On Unix, let Process.destroy() send SIGTERM so Git can remove its lock files.
    // The process-tree terminator uses SIGKILL and prevents Git's signal handlers from running.
    return !SystemInfo.isUnix;
  }
}
