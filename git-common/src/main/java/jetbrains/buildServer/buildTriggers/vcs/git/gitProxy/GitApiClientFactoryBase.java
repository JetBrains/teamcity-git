package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.apache.http.conn.ConnectTimeoutException;
import org.jetbrains.annotations.NotNull;

public class GitApiClientFactoryBase {

  private static final String CLIENT_MAX_CONNECTIONS = "teamcity.git.gitProxy.maxConnections";
  private static final int CLIENT_MAX_CONNECTIONS_DEFAULT = 10;

  @NotNull private final SSLTrustStoreProvider myTrustStoreProvider;
  @NotNull private final HTTPRequestBuilder.ApacheClient43RequestHandler myClient;

  private static final Logger LOG = Logger.getInstance(GitApiClientFactoryBase.class.getName());


  public GitApiClientFactoryBase(@NotNull SSLTrustStoreProvider trustStoreProvider) {
    myTrustStoreProvider = trustStoreProvider;
    myClient = new HTTPRequestBuilder.ApacheClient43RequestHandler();
  }


  public <T> GitApiClient<T> create(@NotNull GitProxySettings proxyCredentials, @NotNull Map<String, String> headers, @NotNull String description, Class<T> clazz, Consumer<Map<String, Object>> additionalArgsBuilder) {
    return new GitApiClient<T>((requestContext) -> clazz.cast(Proxy.newProxyInstance(
      clazz.getClassLoader(),
      new Class[]{clazz},
      new Handler(clazz, proxyCredentials, headers, description, additionalArgsBuilder, requestContext)
    )));
  }

  private class Handler implements InvocationHandler {

    @NotNull private final Class<?> myClass;
    @NotNull private final GitProxySettings myGitProxySettings;
    @NotNull private final String myEndpoint;
    @NotNull private final Map<String, String> myHeaders;
    @NotNull private final String myDescription;
    @NotNull private final GitApiClient.RequestContext myRequestContext;
    private final Consumer<Map<String, Object>> myAdditionalArgsBuilder;
    @NotNull private final Gson myGson;

    public Handler(@NotNull Class<?> clazz, @NotNull GitProxySettings proxyCredentials, @NotNull Map<String, String> headers, @NotNull String description, Consumer<Map<String, Object>> additionalArgsBuilder, @NotNull GitApiClient.RequestContext requestContext) {
      myClass = clazz;
      myEndpoint = proxyCredentials.getUrl();
      myGitProxySettings = proxyCredentials;
      myHeaders = headers;
      myDescription = description;
      myAdditionalArgsBuilder = additionalArgsBuilder;
      myRequestContext = requestContext;
      myGson = new Gson();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getName().equals("hashCode") && args.length == 0) {
        return System.identityHashCode(proxy);
      } else if (method.getName().equals("equals") && args.length == 1) {
        return proxy == args[0];
      } else if (method.getName().equals("toString") && args.length == 0) {
        return String.format("GitApi proxy: %s@%s", myClass.getName(), System.identityHashCode(proxy));
      } else {
        Map<String, Object> namedArgs = IntStream.range(0, args.length)
                                                           .mapToObj(i -> new Pair<>(args[i], method.getParameters()[i].getName()))
                                                           .collect(Collectors.toMap(p -> p.second, p -> p.first));
        return prepareAndCall(method, namedArgs, myAdditionalArgsBuilder);
      }
    }

    private Object prepareAndCall(Method method, Map<String, Object> namedArgs, Consumer<Map<String, Object>> additionalArgsBuilder) throws URISyntaxException {
      Map<String, Object> payloadArgs = new HashMap<>();
      payloadArgs.put("class", myClass.getSimpleName());
      payloadArgs.put("method", method.getName());
      payloadArgs.put("args", namedArgs);
      additionalArgsBuilder.accept(payloadArgs);

      String payload = myGson.toJson(payloadArgs);
      AtomicReference<Exception> exception = new AtomicReference<>();
      AtomicInteger responseCode = new AtomicInteger();
      AtomicReference<Object> responseObject = new AtomicReference<>();
      AtomicReference<String> responseErrorBody = new AtomicReference<>();

      Map<String, String> headers = new HashMap<>(myHeaders);
      String operationId = myRequestContext.getOperationId();
      String requestId = "teamcity" + operationId + "_" + UUID.randomUUID();
      headers.put("X-Request-ID", requestId);

      LOG.info(String.format("Starting request with ID: %s. Operation id %s", requestId, operationId));

      long startTime = System.currentTimeMillis();
      int connectTimeoutMs = myGitProxySettings.getConnectTimeoutMs();
      int retryNum = 0;
      while (System.currentTimeMillis() - startTime < myGitProxySettings.getTimeoutMs() && retryNum <= myGitProxySettings.getConnectRetryCnt()) {
        exception.set(null);
        responseCode.set(0);
        responseObject.set(null);
        responseErrorBody.set(null);

        HTTPRequestBuilder.Request request = new HTTPRequestBuilder(myEndpoint)
          .withTimeout(myGitProxySettings.getTimeoutMs())
          .withConnectionTimeoutMs(connectTimeoutMs)
          .withPreemptiveAuthentication(true)
          .withHeader(headers)
          .withTrustStore(myTrustStoreProvider.getTrustStore())
          .withMaxConnections(TeamCityProperties.getInteger(CLIENT_MAX_CONNECTIONS, CLIENT_MAX_CONNECTIONS_DEFAULT))
          .withData(payload.getBytes(StandardCharsets.UTF_8))
          .withMethod("POST")
          .onException(ex -> {
            exception.set(ex);
          })
          .onErrorResponse(response -> {
            responseCode.set(response.getStatusCode());
            responseErrorBody.set(response.getBodyAsString());
          })
          .onSuccess(response -> {
            responseCode.set(response.getStatusCode());
            InputStream bodyStream = response.getContentStream();
            responseObject.set(myGson.fromJson(
              new InputStreamReader(Objects.requireNonNull(bodyStream, "response body should not be empty")), method.getGenericReturnType()));
          })
          .build();

        IOGuard.allowNetworkCall(() -> myClient.doRequest(request));

        Exception ex = exception.get();
        if (ex != null && ex instanceof ConnectTimeoutException) {
          connectTimeoutMs *= 2;
          retryNum++;
        } else {
          break;
        }
      }

      if (responseCode.get() != 200 || exception.get() != null) {
        LOG.warn(String.format("Git proxy request to %s failed in %d ms.%s%s%s%s Operation id %s",
                               myEndpoint,
                               System.currentTimeMillis() - startTime,
                               responseCode.get() != 0 ? " Response code: " + responseCode.get() + "." : "",
                               responseErrorBody.get() != null ? " Body: " + responseErrorBody.get() + "." : "",
                               exception.get() != null ? " Exception: " + exception.get().toString() + "." : "",
                               getRequestDump(payload),
                               operationId));
        throw new GitApiException("Api request failed", responseCode.get(), responseErrorBody.get(), exception.get());
      }

      return responseObject.get();
    }

    private String getRequestDump(@NotNull String payLoad) {
      StringBuilder builder = new StringBuilder();
      builder.append(String.format(" Request: "));
      if (myHeaders.containsKey("X-Request-ID")) {
        builder.append("X-Request-ID(").append(myHeaders.get("X-Request-ID")).append(") ");
      }
      builder.append(payLoad);
      return builder.toString();
    }
  }
}
