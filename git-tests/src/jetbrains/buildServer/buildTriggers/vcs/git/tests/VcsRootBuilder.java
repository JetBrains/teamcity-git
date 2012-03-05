/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class VcsRootBuilder {

  private Integer myId;
  private String myFetchUrl;
  private String myBranchName;
  private SubmodulesCheckoutPolicy mySubmodulePolicy;
  private String myUsernameForTags;
  private String myUsername;
  private String myPassword;
  private AuthenticationMethod myAuthMethod;

  public static VcsRootBuilder vcsRoot() {
    return new VcsRootBuilder();
  }


  @NotNull
  public VcsRootImpl build() {
    final int id = myId != null ? myId : 1;
    VcsRootImpl result = new VcsRootImpl(id, Constants.VCS_NAME);
    result.addProperty(Constants.FETCH_URL, myFetchUrl);
    result.addProperty(Constants.BRANCH_NAME, myBranchName);
    result.addProperty(Constants.USERNAME_FOR_TAGS, myUsernameForTags);
    if (myUsername != null)
      result.addProperty(Constants.USERNAME, myUsername);
    if (myPassword != null)
      result.addProperty(Constants.PASSWORD, myPassword);
    if (mySubmodulePolicy != null)
      result.addProperty(Constants.SUBMODULES_CHECKOUT, mySubmodulePolicy.name());
    if (myAuthMethod != null)
      result.addProperty(Constants.AUTH_METHOD, myAuthMethod.name());
    return result;
  }


  public VcsRootBuilder withId(int myId) {
    this.myId = myId;
    return this;
  }

  public VcsRootBuilder withFetchUrl(String myFetchUrl) {
    this.myFetchUrl = myFetchUrl;
    return this;
  }

  public VcsRootBuilder withBranch(String myBranchName) {
    this.myBranchName = myBranchName;
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
}
