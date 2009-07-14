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

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import org.spearce.jgit.lib.Config;
import org.spearce.jgit.lib.RepositoryConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Utility that allows working with submodules configuration file
 */
public class SubmodulesConfig {
  /**
   * Repository configuration
   */
  private final RepositoryConfig myRepositoryConfig;
  /**
   * Module configuration
   */
  private final Config myModulesConfig;
  /**
   * Loaded entries
   */
  private final Map<String, Submodule> myPathToEntry = new HashMap<String, Submodule>();
  /**
   * The set of direct parents for submodules
   */
  private final HashSet<String> mySubmoduleDirectParents = new HashSet<String>();
  /**
   * If true the configuration is loaded
   */
  private boolean myIsLoaded = false;

  /**
   * A constructor from configuration files
   *
   * @param repositoryConfig repository configuration
   * @param modulesConfig    modules configuration
   */
  public SubmodulesConfig(RepositoryConfig repositoryConfig, Config modulesConfig) {
    myRepositoryConfig = repositoryConfig;
    myModulesConfig = modulesConfig;
  }

  /**
   * Get entry for the path
   *
   * @param path the entry path
   * @return the entry or null if the entry is not found for the path
   */
  public Submodule findEntry(String path) {
    ensureLoaded();
    return myPathToEntry.get(path);
  }

  /**
   * Ensure that submodule configuration is loaded from database
   */
  private void ensureLoaded() {
    if (myIsLoaded) {
      return;
    }
    for (String name : myModulesConfig.getSubsections("submodule")) {
      final String path = myModulesConfig.getString("submodule", name, "path");
      String url = myRepositoryConfig.getString("submodule", name, "url");
      if (url == null) {
        url = myModulesConfig.getString("submodule", name, "url");
      }
      if (url == null || path == null) {
        // if url or path might be missing in the case of leftover sections
        // in configuration file.
        continue;
      }
      myPathToEntry.put(path, new Submodule(name, path, url));
      int p = path.lastIndexOf('/');
      if (p == -1) {
        p = 0;
      }
      mySubmoduleDirectParents.add(path.substring(0, p));
    }
    myIsLoaded = true;
  }

  /**
   * Check if the specified prefix is a direct submodule parent. This check is used to detect
   * situation when the directory might be reordered due to the submodules.
   *
   * @param path the path to be checked if it is a submoulde parent.
   * @return true if the path can directly contain submodules
   */
  public boolean isSubmodulePrefix(String path) {
    ensureLoaded();
    return mySubmoduleDirectParents.contains(path);
  }
}
