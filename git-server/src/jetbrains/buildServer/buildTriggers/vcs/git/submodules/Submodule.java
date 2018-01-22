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

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import org.jetbrains.annotations.NotNull;

/**
 * The entry in submodule configuration
 */
public class Submodule {
  /**
   * The name of submodule name. It is usually a lowercase path.
   */
  private final String myName;
  /**
   * The path where submodule is mapped
   */
  private final String myPath;
  /**
   * The submodule URL.
   */
  private final String myUrl;

  /**
   * The entry constructor
   *
   * @param name the name of submodule
   * @param path the path in repository
   * @param url  the URL which is submodule is mapped to
   */
  Submodule(@NotNull String name, @NotNull String path, @NotNull String url) {
    myName = name;
    myUrl = url;
    myPath = path;
  }

  /**
   * @return the submodule name
   */
  @NotNull
  public String getName() {
    return myName;
  }

  /**
   * @return the submodule path
   */
  @NotNull
  public String getPath() {
    return myPath;
  }

  /**
   * @return the submodule URL
   */
  @NotNull
  public String getUrl() {
    return myUrl;
  }
}
