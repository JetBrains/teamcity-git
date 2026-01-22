package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.concurrent.TimeUnit;

public class GitCommandRetryPolicy {
  public enum RetryMode {
    NOT_REQUIRED,
    DEFAULT,
    CUSTOM
  }

  public static final long INITIAL_DELAY_MS = TimeUnit.MILLISECONDS.toMillis(5000);

  private final RetryMode myMode;
  private long myDelayMs = -1;

  public GitCommandRetryPolicy() {
    myMode = RetryMode.DEFAULT;
  }

  public GitCommandRetryPolicy(RetryMode mode) {
    myMode = mode;
    if (mode == RetryMode.CUSTOM) {
      myDelayMs = INITIAL_DELAY_MS;
    }
  }

  public GitCommandRetryPolicy(long delayMs) {
    myMode = RetryMode.CUSTOM;
    myDelayMs = delayMs;
  }

  public RetryMode getMode() {
    return myMode;
  }

  public long getDelayMs() {
    return myDelayMs;
  }
}
