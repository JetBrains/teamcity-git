package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitApiException extends RuntimeException {

  private final int myStatusCode;
  @Nullable private final String myBody;

  public GitApiException(@NotNull String message,
                         int statusCode,
                         @Nullable String body,
                         @Nullable Throwable cause) {
    super(message, cause);
    myStatusCode = statusCode;
    myBody = body;
  }

  @Nullable
  public String getBody() {
    return myBody;
  }

  public int getStatusCode() {
    return myStatusCode;
  }
}
