package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.http.HttpUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AgentControlClient {
  private static final String URL_AGENT_SELF_STOP = "/app/agentSelfStop";

  public void stopAgent(@NotNull AgentRunningBuild build, @NotNull StopAction action, @NotNull String reason) throws VcsException {
    sendRequest(build, build.getAgentConfiguration().getServerUrl(), URL_AGENT_SELF_STOP, action.get(), reason);
  }

  enum StopAction {
    DISABLE("disable"), TERMINATE("terminate"), AUTO("auto");

    @NotNull private final String myAction;
    @NotNull private static final Map<String, StopAction> myActions = new HashMap<>();

    StopAction(@NotNull String action) {
      myAction = action;
    }

    public String get() {
      return myAction;
    }

    @Nullable
    public static StopAction getAction(@NotNull String action) {
      return myActions.get(action);
    }

    static {
      for (final StopAction sa : StopAction.values()) {
        myActions.put(sa.get(), sa);
      }
    }
  }


    private void sendRequest(@NotNull AgentRunningBuild build, @NotNull String serverUrl, @NotNull String endPoint,
                          @NotNull String action, @NotNull String reason) throws VcsException{
    PostMethod post = new PostMethod(serverUrl + endPoint);
    HttpUserAgent.addHeader(post);
    post.setDoAuthentication(true);
    post.addParameter("reason", reason);
    post.addParameter("action", action);

    try {
      final HttpClient client = prepareHttpClient(serverUrl, build);
      int code = client.executeMethod(post);
      if (code >= 300) {
        throw new VcsException("Request to stop the build has failed with an HTTP code " + code);
      }
    } catch (IOException ex) {
      throw new VcsException("Request to stop the build has failed with an exception", ex);
    }
  }

  @NotNull
  private static HttpClient prepareHttpClient(@NotNull String url, @NotNull AgentRunningBuild build) throws MalformedURLException {

    URL serverURL;
    serverURL = new URL(url);

    UsernamePasswordCredentials cre = new UsernamePasswordCredentials(build.getAccessUser(), build.getAccessCode());
    HttpClient httpClient = HttpUtil.createHttpClient(build.getAgentConfiguration().getServerConnectionTimeout(), serverURL, cre);

    BuildAgentConfigurationEx configuration = (BuildAgentConfigurationEx)build.getAgentConfiguration();
    if (configuration.getServerProxyHost() != null) {
      HttpUtil.configureProxy(httpClient, configuration.getServerProxyHost(), configuration.getServerProxyPort(), configuration.getServerProxyCredentials());
    }

    httpClient.getParams().setParameter(HttpMethodParams.USER_AGENT, HttpUserAgent.getUserAgent());

    return httpClient;
  }

}
