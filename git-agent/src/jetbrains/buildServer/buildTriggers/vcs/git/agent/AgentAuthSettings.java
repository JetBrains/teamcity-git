package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.util.function.Function;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettingsImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelper;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.agent.oauth.AgentTokenStorage.TOKEN_ID_PREFIX;

public class AgentAuthSettings extends AuthSettingsImpl implements AuthSettings  {

  public AgentAuthSettings(GitVcsRoot root, URIishHelper urIishHelper,  @Nullable Function<String, String> tokenRetriever) {
    super(root, urIishHelper, tokenRetriever);
  }

  @Override
  protected boolean isTokenId(@Nullable String passwordValue) {
    return passwordValue != null && passwordValue.startsWith(TOKEN_ID_PREFIX);
  }
}
