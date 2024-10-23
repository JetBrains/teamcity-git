package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.HTTPRequestBuilder;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.jetbrains.annotations.NotNull;

public class GitApiClientFactoryBase {

  private static final String REQUEST_TIMEOUT_PROPERTY = "teamcity.git.gitProxy.requestTimeoutSeconds";

  @NotNull private final SSLTrustStoreProvider myTrustStoreProvider;
  @NotNull private final HTTPRequestBuilder.ApacheClient43RequestHandler myClient;

  private static final Logger LOG = Logger.getInstance(GitApiClientFactoryBase.class.getName());


  public GitApiClientFactoryBase(@NotNull SSLTrustStoreProvider trustStoreProvider) {
    myTrustStoreProvider = trustStoreProvider;
    myClient = new HTTPRequestBuilder.ApacheClient43RequestHandler();
  }


  public <T> T create(@NotNull String endpoint, @NotNull Map<String, String> headers, @NotNull String description, Class<T> clazz, Consumer<Map<String, Object>> additionalArgsBuilder) {
    return clazz.cast(Proxy.newProxyInstance(
      clazz.getClassLoader(),
      new Class[]{clazz},
      new Handler(clazz, endpoint, headers, description, additionalArgsBuilder)
    ));
  }

  private class Handler implements InvocationHandler {

    @NotNull private final Class<?> myClass;
    @NotNull private final String myEndpoint;
    @NotNull private final Map<String, String> myHeaders;
    @NotNull private final String myDescription;
    private final Consumer<Map<String, Object>> myAdditionalArgsBuilder;
    @NotNull private final Gson myGson;

    public Handler(@NotNull Class<?> clazz, @NotNull String endpoint, @NotNull Map<String, String> headers, @NotNull String description, Consumer<Map<String, Object>> additionalArgsBuilder) {
      myClass = clazz;
      myEndpoint = endpoint;
      myHeaders = headers;
      myDescription = description;
      myAdditionalArgsBuilder = additionalArgsBuilder;
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
      AtomicReference<String> responseBody = new AtomicReference<>();

      long startTime = System.currentTimeMillis();
      HTTPRequestBuilder.Request request = new HTTPRequestBuilder(myEndpoint)
        .withTimeout(TeamCityProperties.getInteger(REQUEST_TIMEOUT_PROPERTY, 20) * 1000)
        .withPreemptiveAuthentication(true)
        .withHeader(myHeaders)
        .withTrustStore(myTrustStoreProvider.getTrustStore())
        .withData(payload.getBytes(StandardCharsets.UTF_8))
        .withMethod("POST")
        .onException(ex -> {
          exception.set(ex);
        })
        .onErrorResponse(response -> {
          responseCode.set(response.getStatusCode());
          responseBody.set(response.getBodyAsString());
        })
        .onSuccess(response -> {
          responseCode.set(response.getStatusCode());
          responseBody.set(response.getBodyAsString());
        })
        .build();

      IOGuard.allowNetworkCall(() -> myClient.doRequest(request));

      if (responseCode.get() != 200 || exception.get() != null) {
        LOG.warn(String.format("Git proxy request to %s failed in %d ms.%s%s%s%s",
                               myEndpoint,
                               System.currentTimeMillis() - startTime,
                               responseCode.get() != 0 ? " Response code: " + responseCode.get() + "." : "",
                               responseBody.get() != null ? " Body: " + responseBody.get() + "." : "",
                               exception.get() != null ? " Exception: " + exception.get().toString() + "." : "",
                               getRequestDump(payload)));
        throw new GitApiException("Api request failed", responseCode.get(), responseBody.get(), exception.get());
      }

      return myGson.fromJson(responseBody.get(), method.getGenericReturnType());
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
