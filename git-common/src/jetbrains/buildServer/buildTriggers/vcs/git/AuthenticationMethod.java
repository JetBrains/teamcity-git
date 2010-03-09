/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
  ANONYMOUS,
  /**
   * The default SSH private key
   */
  PRIVATE_KEY_DEFAULT,
  /**
   * The private key is specified in the file
   */
  PRIVATE_KEY_FILE,
  /**
   * The password is used
   */
  PASSWORD
}
