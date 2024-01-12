

package jetbrains.buildServer.buildTriggers.vcs.git.command.credentials;

import com.intellij.openapi.util.Trinity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.CredentialsHelper.*;

public class CredentialsHelperConfig {
  private final List<Trinity<String, String, String>> myCredentials = new ArrayList<Trinity<String, String, String>>();
  private boolean myMatchAllUrls;

  public void addCredentials(@NotNull String url, @Nullable String user, @NotNull String password) {
    myCredentials.add(Trinity.create(url, user, password));
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
      result.put(credEnv(i, CRED_PWD), credential.third);
    }
    if (myMatchAllUrls)
      result.put(credEnv(CRED_MATCH_ALL_URLS), "true");
    return result;
  }
}