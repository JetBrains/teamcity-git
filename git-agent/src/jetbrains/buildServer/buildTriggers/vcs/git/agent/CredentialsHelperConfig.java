/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.openapi.util.Trinity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.buildTriggers.vcs.git.agent.CredentialsHelper.*;

public class CredentialsHelperConfig {
  private final List<Trinity<String, String, String>> myCredentials = new ArrayList<Trinity<String, String, String>>();
  private boolean myMatchAllUrls;

  public void addCredentials(@NotNull String url, @NotNull String user, @NotNull String password) {
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
      result.put(credEnv(i, CRED_USER), credential.second);
      result.put(credEnv(i, CRED_PWD), credential.third);
    }
    if (myMatchAllUrls)
      result.put(credEnv(CRED_MATCH_ALL_URLS), "true");
    return result;
  }
}
