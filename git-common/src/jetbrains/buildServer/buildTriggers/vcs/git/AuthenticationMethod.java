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

/**
 * Authentication method
 */
public enum AuthenticationMethod {
  /**
   * Anonymous access (or password is a part of URL)
   */
  ANONYMOUS("Anonymous"),
  /**
   * The default SSH private key
   */
  PRIVATE_KEY_DEFAULT("Default Private Key"),
  /**
   * The private key is specified in the file
   */
  PRIVATE_KEY_FILE("Private Key"),
  /**
   * The password is used
   */
  PASSWORD("Password"),

  TEAMCITY_SSH_KEY("TeamCity SSH Key");

  /**
   * Name of auth method for user, e.g. in error messages
   */
  private final String myUIName;

  AuthenticationMethod(String uiName) {
    myUIName = uiName;
  }

  public String uiName() {
    return myUIName;
  }
}
