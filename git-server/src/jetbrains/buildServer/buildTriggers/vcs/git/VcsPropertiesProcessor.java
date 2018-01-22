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

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.buildTriggers.vcs.AbstractVcsPropertiesProcessor;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
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

  public Collection<InvalidProperty> process(Map<String, String> properties) {
    Collection<InvalidProperty> rc = new LinkedList<InvalidProperty>();
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
      }
    }

    rc.addAll(validateBranchName(properties));
    rc.addAll(validateBranchSpec(properties));

    String authMethod = properties.get(Constants.AUTH_METHOD);
    AuthenticationMethod authenticationMethod = authMethod == null ?
                                                AuthenticationMethod.ANONYMOUS : Enum.valueOf(AuthenticationMethod.class, authMethod);
    if (authenticationMethod == AuthenticationMethod.PRIVATE_KEY_FILE) {
      String pkFile = properties.get(Constants.PRIVATE_KEY_PATH);
      if (isEmpty(pkFile)) {
        rc.add(new InvalidProperty(Constants.PRIVATE_KEY_PATH, "The private key path must be specified."));
      }
    }
    if (authenticationMethod == AuthenticationMethod.TEAMCITY_SSH_KEY) {
      String keyId = properties.get(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME);
      if (isEmpty(keyId))
        rc.add(new InvalidProperty(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME, "The Uploaded key must be specified."));
    }
    return rc;
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
    return error != null ? singleton(error) : Collections.<InvalidProperty>emptySet();
  }


  private String[] splitByLines(@NotNull String s) {
    return EOL_SPLIT_PATTERN.split(s);
  }
}
