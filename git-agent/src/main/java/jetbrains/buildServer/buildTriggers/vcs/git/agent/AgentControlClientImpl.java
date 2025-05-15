package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.agent.impl.BuildAgentEx;
import jetbrains.buildServer.http.HttpUserAgent;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jetbrains.annotations.NotNull;

public class AgentControlClientImpl implements AgentControlClient {
  private static final String URL_AGENT_DISABLE = "/app/agentParameters/disable";
  private static final String URL_AGENT_TERMINATE = "/app/agentParameters/terminate";
  private final BuildAgentEx myBuildAgent;
  private final BuildAgentConfigurationEx myConfig;
  final private HttpClient myHttpClient;

  public AgentControlClientImpl(@NotNull BuildAgentEx buildAgent, @NotNull BuildAgentConfigurationEx config) {
    myBuildAgent = buildAgent;
    myConfig = config;
    myHttpClient = new HttpClient();
  }

  @Override
  public void terminateAgent(String reason) {
    sendRequest(myConfig.getServerUrl() + URL_AGENT_TERMINATE, reason);
  }

  @Override
  public void disableAgent(@NotNull String reason) {
    sendRequest(myConfig.getServerUrl() + URL_AGENT_DISABLE, reason);
  }


  public void sendRequest(@NotNull String url, @NotNull String reason) {
    PostMethod post = new PostMethod(url);
    HttpUserAgent.addHeader(post);
    final Integer agentId = myBuildAgent.getId();
    String pingCode = myConfig.getPingCode();
    if (agentId == null || StringUtil.isEmpty(pingCode)) {
      return;
    }
    final String unpwEncoded = Base64.getEncoder().encodeToString(String.format("%d:%s", agentId, pingCode).getBytes(StandardCharsets.UTF_8));
    post.setRequestHeader("Authorization", "Basic "+ unpwEncoded);
    post.addParameter("reason", reason);
    try {
      int code = myHttpClient.executeMethod(post);
    } catch(IOException ex) {
      // TODO: handle the exception
    }
  }

}
