

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.buildTriggers.vcs.AbstractVcsPropertiesProcessor;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;

import static java.util.Collections.singleton;

/**
 * @author dmitry.neverov
 */
public class VcsPropertiesProcessor extends AbstractVcsPropertiesProcessor {

  private static final Pattern EOL_SPLIT_PATTERN = Pattern.compile("(\r|\n|\r\n)");

  @NotNull
  private final ServerPluginConfig myConfig;

  public VcsPropertiesProcessor(@NotNull ServerPluginConfig config) {
    myConfig = config;
  }

  public Collection<InvalidProperty> process(Map<String, String> properties) {
    Collection<InvalidProperty> rc = new LinkedList<InvalidProperty>();

    String authMethod = properties.get(Constants.AUTH_METHOD);
    AuthenticationMethod authenticationMethod = authMethod == null ?
                                                AuthenticationMethod.ANONYMOUS : Enum.valueOf(AuthenticationMethod.class, authMethod);

    String url = properties.get(Constants.FETCH_URL);
    if (isEmpty(url)) {
      rc.add(new InvalidProperty(Constants.FETCH_URL, "The URL must be specified"));
    } else {
      if (url.contains("\n") || url.contains("\r")) {
        rc.add(new InvalidProperty(Constants.FETCH_URL, "URL should not contain newline symbols"));
      } else if (!mayContainReference(url)) {
        try {
          new URIish(url);
        } catch (URISyntaxException e) {
          rc.add(new InvalidProperty(Constants.FETCH_URL, "Invalid URL syntax: " + url));
        }

        try {
          validateUrlAuthMethod(url, authenticationMethod, "fetch");
        } catch (VcsException e) {
          rc.add(new InvalidProperty(Constants.FETCH_URL, e.getMessage()));
          rc.add(new InvalidProperty(Constants.AUTH_METHOD, e.getMessage()));
        }

        if (!myConfig.isAllowFileUrl() && GitRemoteUrlInspector.isLocalFileAccess(url)) {
          rc.add(new InvalidProperty(Constants.FETCH_URL, "The URL most not be a local file URL"));
        }
      }
    }
    String pushUrl = properties.get(Constants.PUSH_URL);
    if (!isEmpty(pushUrl)) {
      if (pushUrl.contains("\n") || pushUrl.contains("\r")) {
        rc.add(new InvalidProperty(Constants.PUSH_URL, "URL should not contain newline symbols"));
      } else if (!mayContainReference(pushUrl)) {
        try {
          new URIish(pushUrl);
        } catch (URISyntaxException e) {
          rc.add(new InvalidProperty(Constants.PUSH_URL, "Invalid URL syntax: " + pushUrl));
        }

        try {
          validateUrlAuthMethod(pushUrl, authenticationMethod, "push");
        } catch (VcsException e) {
          rc.add(new InvalidProperty(Constants.PUSH_URL, e.getMessage()));
          rc.add(new InvalidProperty(Constants.AUTH_METHOD, e.getMessage()));
        }

        if (!myConfig.isAllowFileUrl() && GitRemoteUrlInspector.isLocalFileAccess(pushUrl)) {
          rc.add(new InvalidProperty(Constants.PUSH_URL, "The URL most not be a local file URL"));
        }
      }
    }

    rc.addAll(validateBranchName(properties));
    rc.addAll(validateBranchSpec(properties));

    switch (authenticationMethod) {
      case PRIVATE_KEY_FILE:
        String pkFile = properties.get(Constants.PRIVATE_KEY_PATH);
        if (isEmpty(pkFile)) {
          rc.add(new InvalidProperty(Constants.PRIVATE_KEY_PATH, "The private key path must be specified."));
        }
        break;

      case TEAMCITY_SSH_KEY:
        String keyId = properties.get(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME);
        if (isEmpty(keyId)) {
          rc.add(new InvalidProperty(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME, "The Uploaded key must be specified."));
        }
        break;

      case ACCESS_TOKEN:
        final String tokenId = properties.get(Constants.TOKEN_ID);
        if (StringUtil.isEmptyOrSpaces(tokenId)) {
          rc.add(new InvalidProperty(Constants.TOKEN_ID, "The access token must be specified."));
        }
        break;
    }

    return rc;
  }

  /**
   * @throws VcsException if the url is incompatible with the authMethod
   */
  public static void validateUrlAuthMethod(@NotNull String url, @NotNull AuthenticationMethod authMethod, @NotNull String urlName) throws VcsException {
    url = url.toLowerCase(Locale.ROOT);
    if (url.contains("\n") || url.contains("\r"))
      throw new VcsException("Newline in " + urlName + " url '" + url + "'");

    String protocol = "";
    boolean invalid = false;
    if (url.startsWith("http://")) {
      if (authMethod.isKeyAuth()) {
        protocol = "http";
        invalid = true;
      }
    } else if (url.startsWith("https://")) {
      if (authMethod.isKeyAuth()) {
        protocol = "https";
        invalid = true;
      }
    } else if (url.startsWith("ssh://")) {
      if (!authMethod.isKeyAuth()) {
        protocol = "ssh";
        invalid = true;
      }
    } else {
      int index1 = url.indexOf('@');
      int index2 = url.indexOf(':');
      if (index1 > 0 && index2 > index1 + 1 && index2 < url.length() - 1 && !authMethod.isKeyAuth()) { // user@server:project.git
        protocol = "ssh";
        invalid = true;
      }
    }

    if (invalid) {
      throw new VcsException(String.format("The '%s' authentication method is incompatible with the '%s' protocol of the %s url", authMethod.uiName(), protocol, urlName));
    }

  }

  public Collection<InvalidProperty> validateBranchName(@NotNull Map<String, String> properties) {
    String branchName = properties.get(Constants.BRANCH_NAME);
    Collection<InvalidProperty> errors = new ArrayList<InvalidProperty>();
    if (StringUtil.isEmptyOrSpaces(branchName)) {
      errors.add(new InvalidProperty(Constants.BRANCH_NAME, "Branch name must be specified"));
      return errors;
    }
    if (branchName.startsWith("/")) {
      errors.add(new InvalidProperty(Constants.BRANCH_NAME, "Branch name should not start with /"));
    }
    return errors;
  }

  @Nullable
  public InvalidProperty validateBranchSpec(@Nullable String branchSpec) {
    if (isEmpty(branchSpec))
      return null;

    assert branchSpec != null;

    int i = 1;
    for (String line : splitByLines(branchSpec)) {
      if (line.startsWith("+:/") || line.startsWith("-:/") || line.startsWith("/")) {
        return new InvalidProperty(Constants.BRANCH_SPEC, "Line " + i + ": pattern should not start with /");
      }
      i++;
    }
    return null;
  }

  public Collection<InvalidProperty> validateBranchSpec(@NotNull Map<String, String> properties) {
    String branchSpec = properties.get(Constants.BRANCH_SPEC);
    InvalidProperty error = validateBranchSpec(branchSpec);
    return error != null ? singleton(error) : Collections.emptySet();
  }


  private String[] splitByLines(@NotNull String s) {
    return EOL_SPLIT_PATTERN.split(s);
  }
}