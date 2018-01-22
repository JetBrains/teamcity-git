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

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.StoredConfig;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Utility that allows working with submodules configuration file
 */
public class SubmodulesConfig {
  /**
   * logger instance
   */
  private static Logger LOG = Logger.getInstance(SubmodulesConfig.class.getName());
  /**
   * Repository configuration
   */
  private final StoredConfig myRepositoryConfig;
  /**
   * Module configuration
   */
  private final Config myModulesConfig;
  /**
   * submodule path -> submodule
   */
  private final Map<String, Submodule> myPathToSubmodule = new HashMap<String, Submodule>();
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
  public SubmodulesConfig(StoredConfig repositoryConfig, Config modulesConfig) {
    myRepositoryConfig = repositoryConfig;
    myModulesConfig = modulesConfig;
  }

  @NotNull
  public Collection<Submodule> getSubmodules() {
    ensureLoaded();
    return myPathToSubmodule.values();
  }

  /**
   * Tests if there is at least one git submodule under the given path
   * @param path path to test
   * @return true is yes
   */
  public boolean containsSubmodule(@NotNull final String path) {
    ensureLoaded();

    for (String subPath : myPathToSubmodule.keySet()) {
      if (subPath.startsWith(path + "/")) return true;
    }
    return false;
  }

  /**
   * Get submodule for the path
   *
   * @param path the entry path
   * @return the submodule or null if submodule is not found for the path
   */
  public Submodule findSubmodule(String path) {
    ensureLoaded();
    return myPathToSubmodule.get(path);
  }

  /**
   * Check if the specified prefix is a direct submodule parent. This check is used to detect
   * situation when the directory might be reordered due to the submodules.
   *
   * @param path the path to be checked if it is a submodule parent.
   * @return true if the path can directly contain submodules
   */
  public boolean isSubmodulePrefix(String path) {
    ensureLoaded();
    return mySubmoduleDirectParents.contains(path);
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
        LOG.warn("Invalid submodule entry: " + name + " path: " + path + " url :" + url);
        continue;
      }
      myPathToSubmodule.put(path, new Submodule(name, path, url));
      int p = path.lastIndexOf('/');
      if (p == -1) {
        p = 0;
      }
      mySubmoduleDirectParents.add(path.substring(0, p));
    }
    myIsLoaded = true;
  }
}
