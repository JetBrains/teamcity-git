package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitApiClient<T> {
  private final ApiConstructor<T> myConstructor;
  @Nullable
  private String myOperationId;

  public GitApiClient(@NotNull ApiConstructor<T> constructor) {
    myConstructor = constructor;
    myOperationId = null;
  }

  public GitApiClient<T> withOperationId(@Nullable String operationId) {
    myOperationId = operationId;
    return this;
  }

  public T newRequest(@Nullable String operationId) {
    return myConstructor.create(new RequestContext(operationId));
  }

  public T newRequest() {
    return newRequest(myOperationId);
  }

  @Nullable
  public String getOperationId() {
    return myOperationId;
  }

  public interface ApiConstructor<T> {
    T create(RequestContext context);
  }

  public static class RequestContext {
    @Nullable
    private final String myOperationId;

    private RequestContext(@Nullable String requestId) {
      myOperationId = requestId;
    }

    @NotNull
    public String getOperationId() {
      return myOperationId == null ? "" : myOperationId;
    }
  }
}
