

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.io.IOException;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.vcs.VcsUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class VcsRootBuilder {

  private Integer myId;
  private String myFetchUrl;
  private String myPushUrl;
  private String myBranchName;
  private String myBranchSpec;
  private SubmodulesCheckoutPolicy mySubmodulePolicy;
  private String myUsernameForTags;
  private String myUsername;
  private String myPassword;
  private AuthenticationMethod myAuthMethod;
  private String myPath;
  private String myAgentGitPath;
  private boolean myAutoCrlf = false;
  private boolean myReportTags = false;
  private Boolean myUseMirrors;
  private boolean myIgnoreKnownHosts = true;
  private String myPrivateKeyPath;
  private String myTeamCitySshKey;
  private String myPassphrase;
  private String myRequestToken;
  private String myTokenId;

  public static VcsRootBuilder vcsRoot() {
    return new VcsRootBuilder();
  }


  @NotNull
  public VcsRootImpl build() {
    final int id = myId != null ? myId : 1;
    VcsRootImpl result = new VcsRootImpl(id, Constants.VCS_NAME);
    result.setName(myFetchUrl);
    result.addProperty(VcsUtil.VCS_NAME_PROP, Constants.VCS_NAME);
    result.addProperty(Constants.FETCH_URL, myFetchUrl);
    if (myPushUrl != null)
      result.addProperty(Constants.PUSH_URL, myPushUrl);
    result.addProperty(Constants.BRANCH_NAME, myBranchName);
    result.addProperty(Constants.USERNAME_FOR_TAGS, myUsernameForTags);
    result.addProperty(Constants.BRANCH_SPEC, myBranchSpec);
    if (myUseMirrors != null)
      result.addProperty(Constants.CHECKOUT_POLICY, String.valueOf(myUseMirrors));
    if (myUsername != null)
      result.addProperty(Constants.USERNAME, myUsername);
    if (myPassword != null)
      result.addProperty(Constants.PASSWORD, myPassword);
    if (mySubmodulePolicy != null)
      result.addProperty(Constants.SUBMODULES_CHECKOUT, mySubmodulePolicy.name());
    if (myAuthMethod != null)
      result.addProperty(Constants.AUTH_METHOD, myAuthMethod.name());
    if (myPath != null)
      result.addProperty(Constants.PATH, myPath);
    if (myAgentGitPath != null)
      result.addProperty(Constants.AGENT_GIT_PATH, myAgentGitPath);
    if (myPrivateKeyPath != null) {
      result.addProperty(Constants.PRIVATE_KEY_PATH, myPrivateKeyPath);
    }
    if (myTeamCitySshKey != null) {
      result.addProperty("teamcitySshKey", myTeamCitySshKey);
    }
    if (myPassphrase != null) {
      result.addProperty(Constants.PASSPHRASE, myPassphrase);
    }
    if (myRequestToken != null) {
      result.addProperty(PluginConfigImpl.SSH_SEND_ENV_REQUEST_TOKEN, myRequestToken);
    }
    if (myTokenId != null) {
      result.addProperty(Constants.TOKEN_ID, myTokenId);
    }

    result.addProperty(Constants.SERVER_SIDE_AUTO_CRLF, String.valueOf(myAutoCrlf));
    result.addProperty(Constants.REPORT_TAG_REVISIONS, String.valueOf(myReportTags));
    result.addProperty(Constants.IGNORE_KNOWN_HOSTS, String.valueOf(myIgnoreKnownHosts));
    return result;
  }


  public VcsRootBuilder withId(int id) {
    myId = id;
    return this;
  }

  public VcsRootBuilder withFetchUrl(String fetchUrl) {
    myFetchUrl = fetchUrl;
    return this;
  }

  public VcsRootBuilder withPushUrl(String pushUrl) {
    myPushUrl = pushUrl;
    return this;
  }

  public VcsRootBuilder withFetchUrl(@NotNull File remoteRepositoryDir) throws IOException {
    myFetchUrl = remoteRepositoryDir.getCanonicalPath();
    return this;
  }

  public VcsRootBuilder withRepositoryPathOnServer(String path) {
    myPath = path;
    return this;
  }

  public VcsRootBuilder withBranch(String branchName) {
    myBranchName = branchName;
    return this;
  }

  public VcsRootBuilder withBranchSpec(String branchSpec) {
    myBranchSpec = branchSpec;
    return this;
  }

  public VcsRootBuilder withSubmodulePolicy(SubmodulesCheckoutPolicy policy) {
    mySubmodulePolicy = policy;
    return this;
  }


  public VcsRootBuilder withUsernameForTags(String username) {
    myUsernameForTags = username;
    return this;
  }

  public VcsRootBuilder withUsername(String username) {
    myUsername = username;
    return this;
  }

  public VcsRootBuilder withPassword(String password) {
    myPassword = password;
    return this;
  }

  public VcsRootBuilder withAuthMethod(AuthenticationMethod authMethod) {
    myAuthMethod = authMethod;
    return this;
  }

  public VcsRootBuilder withAgentGitPath(String agentGitPath) {
    myAgentGitPath = agentGitPath;
    return this;
  }

  public VcsRootBuilder withAutoCrlf(boolean autoCrlf) {
    myAutoCrlf = autoCrlf;
    return this;
  }

  public VcsRootBuilder withReportTags(boolean doReportTags) {
    myReportTags = doReportTags;
    return this;
  }

  public VcsRootBuilder withUseMirrors(boolean doUseMirrors) {
    myUseMirrors = doUseMirrors;
    return this;
  }

  public VcsRootBuilder withIgnoreKnownHosts(boolean ignore) {
    myIgnoreKnownHosts = ignore;
    return this;
  }

  public VcsRootBuilder withPrivateKeyPath(String privateKeyPath) {
    myPrivateKeyPath = privateKeyPath;
    return this;
  }

  public VcsRootBuilder withTeamCitySshKey(String teamCitySshKey) {
    myTeamCitySshKey = teamCitySshKey;
    return this;
  }

  public VcsRootBuilder withPassphrase(String passphrase) {
    myPassphrase = passphrase;
    return this;
  }

  public VcsRootBuilder withRequestToken(String requestToken) {
    myRequestToken = requestToken;
    return this;
  }

  public VcsRootBuilder withTokenId(String tokenId) {
    myTokenId = tokenId;
    return this;
  }
}