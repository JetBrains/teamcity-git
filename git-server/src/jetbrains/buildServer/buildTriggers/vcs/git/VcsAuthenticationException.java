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

import jetbrains.buildServer.vcs.VcsException;

/**
 * Exception is thrown when some authentication problem occurred while processing vcs request.
 * @author dmitry.neverov
 */
public class VcsAuthenticationException extends VcsException {

  public VcsAuthenticationException(String message) {
    super(message);
  }

  public VcsAuthenticationException(String repositoryUrl, String message) {
    this(formatErrorMessage(repositoryUrl, message));
  }

  public VcsAuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }

  public VcsAuthenticationException(String repositoryUrl, String message, Throwable e) {
    this(formatErrorMessage(repositoryUrl, message), e);
  }

  private static String formatErrorMessage(String repositoryUrl, String message) {
    return String.format("Repository '%s': %s", repositoryUrl, message);
  }
}
