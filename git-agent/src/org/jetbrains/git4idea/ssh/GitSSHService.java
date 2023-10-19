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

package org.jetbrains.git4idea.ssh;

import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The provider of SSH scripts for the Git
 */
public abstract class GitSSHService {

  /**
   * Path to the generated script
   */
  protected File myScript;
  protected String myScriptPath;

  /**
   * @return the port number for XML RCP
   */
  public abstract int getXmlRcpPort();

  /**
   * Get file to the script service
   *
   * @return path to the script
   * @throws IOException if script cannot be generated
   */
  @NotNull
  public abstract String getScriptPath() throws IOException;

  /**
   * @return the temporary directory to use or null if the default directory might be used
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  @Nullable
  protected File getTempDir() {
    return null;
  }


  /**
   * Handler interface to use by the client code
   */
  public interface Handler {

  }

}
