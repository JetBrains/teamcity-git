/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import jetbrains.buildServer.vcs.VcsRoot;

/**
 * The configuration constants
 */
public interface Constants {
  /**
   * The URL property
   */
  public static String URL = "url";
  /**
   * The path property
   */
  public static String PATH = "path";
  /**
   * The branch name property
   */
  public static String BRANCH_NAME = "branch";
  /**
   * The branch name property
   */
  public static String SUBMODULES_CHECKOUT = "submoduleCheckout";
  /**
   * The user name property
   */
  public static String AUTH_METHOD = "authMethod";
  /**
   * The user name property
   */
  public static String USERNAME = "username";
  /**
   * The user name property
   */
  public static String PRIVATE_KEY_PATH = "privateKeyPath";
  /**
   * The password property name
   */
  public static String PASSWORD = VcsRoot.SECURE_PROPERTY_PREFIX + "password";
  /**
   * The password property name
   */
  public static String PASSPHRASE = VcsRoot.SECURE_PROPERTY_PREFIX + "passphrase";
  /**
   * The vcs name
   */
  public static String VCS_NAME = "jetbrains.git";
  /**
   * The user name property
   */
  public static String USERNAME_STYLE = "usernameStyle";
  /**
   * The ignore known hosts property
   */
  public static String IGNORE_KNOWN_HOSTS = "ignoreKnownHosts";
}
