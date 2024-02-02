

package jetbrains.buildServer.buildTriggers.vcs.git.command.credentials;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Trinity;
import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.ExtraHTTPCredentials;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitFacadeImpl;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import jetbrains.buildServer.buildTriggers.vcs.git.command.*;

import static jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.CredentialsHelper.*;

public class CredentialsHelperConfig {
  private final List<Trinity<String, String, String>> myCredentials = new ArrayList<Trinity<String, String, String>>();
  private static final Logger LOG = Logger.getInstance(CredentialsHelperConfig.class);
  private boolean myMatchAllUrls;

  public void addCredentials(@NotNull String url, @Nullable String user, @NotNull String password) {
    myCredentials.add(Trinity.create(url, user, password));
  }

  public void addCredentials(@NotNull ExtraHTTPCredentials credentials) {
    String actualPassword;
    if (credentials.isRefreshableToken()) {
      actualPassword = credentials.getToken();
    }
    else {
      actualPassword = credentials.getPassword();
    }

    myCredentials.add(Trinity.create(credentials.getUrl(), credentials.getUsername(), actualPassword));
  }

  public void setMatchAllUrls(boolean matchAllUrls) {
    myMatchAllUrls = matchAllUrls;
  }

  @NotNull
  public Map<String, String> getEnv() {
    Map<String, String> result = new HashMap<String, String>();
    int i = 1;
    for (Trinity<String, String, String> credential : myCredentials) {
      result.put(credEnv(i, CRED_URL), credential.first);
      if (StringUtil.isNotEmpty(credential.second)) {
        result.put(credEnv(i, CRED_USER), credential.second);
      }
      if (StringUtil.isNotEmpty(credential.third)) {
        result.put(credEnv(i, CRED_PWD), credential.third);
      }
      ++i;
    }
    if (myMatchAllUrls)
      result.put(credEnv(CRED_MATCH_ALL_URLS), "true");
    return result;
  }

  @Nullable
  public static String configureLfsUrl(@Nullable String url) {
    if (url == null) {
      return null;
    }

    URIish uri;
    try {
      uri = new URIish(url);
    } catch (URISyntaxException e) {
      return null;
    }

    String scheme = uri.getScheme();
    if ("http".equals(scheme) || "https".equals(scheme)) {
      String lfsUrl = uri.setPass("").setUser("").toASCIIString();
      if (lfsUrl.endsWith(".git")) {
        lfsUrl += "/info/lfs";
      } else {
        lfsUrl += lfsUrl.endsWith("/") ? ".git/info/lfs" : "/.git/info/lfs";
      }
      return lfsUrl;
    }
    else {
      return null;
    }
  }

  @Nullable
  public static String configureCredentialHelperScript(@NotNull Context ctx, @NotNull ScriptGen scriptGen) {
    File credentialsHelper = null;
    try {
      final File credentialHelper = scriptGen.generateCredentialHelper();
      credentialsHelper = credentialHelper;

      String path = credentialHelper.getCanonicalPath();
      path = path.replaceAll("\\\\", "/");
      return path;
    } catch (Exception e) {
      if (credentialsHelper != null)
        FileUtil.delete(credentialsHelper);

      final String msg = "Exception while creating credential.helper script: " + e.getMessage();
      LOG.warnAndDebugDetails(msg, e);
    }

    return null;
  }
}