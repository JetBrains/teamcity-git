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

import org.spearce.jgit.lib.BlobBasedConfig;
import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Repository;

import java.io.IOException;

/**
 * The resolver for submodules
 */
public abstract class SubmoduleResolver {
  /**
   * The base for submodule configuration
   */
  final Commit myCommit;
  /**
   * The submodule configuration
   */
  SubmodulesConfig myConfig;

  /**
   * The comimt for that contains submodule reference
   *
   * @param commit the submodule commit
   */
  public SubmoduleResolver(Commit commit) {
    myCommit = commit;
  }

  /**
   * Resolve the commit for submodule
   *
   * @param path   the within repository path
   * @param commit the commit identifier
   * @return the the resoled commit in other repository
   * @throws IOException if there is an IO problem during resolving repositoyr or mapping commit
   */
  public Commit getSubmodule(String path, ObjectId commit) throws IOException {
    ensureConfigLoaded();
    final Submodule submodule = myConfig.findEntry(path);
    Repository r = resolveRepository(path, submodule.getUrl());
    final Commit c = r.mapCommit(commit);
    if (c == null) {
      throw new IOException("The commit " + commit + " (referenced by " + path + ") is not found in repository: " + r);
    }
    return c;
  }

  /**
   * Ensure that submodule configuration has been loaded.
   */
  private void ensureConfigLoaded() {
    if (myConfig == null) {
      myConfig = new SubmodulesConfig(myCommit.getRepository().getConfig(), new BlobBasedConfig(null, myCommit, ".gitmodules"));
    }
  }

  /**
   * Get repository by the URL. Note that the repository is retrived but not cleaned up. This should be done by implementer of this component at later time.
   *
   * @param path the local path within repository
   * @param url  the URL to resolve  @return the resolved repository
   * @return the resolved repository
   * @throws IOException if repository could not be resolved
   */
  protected abstract Repository resolveRepository(String path, String url) throws IOException;

  /**
   * Get submodule resolver for the path
   *
   * @param commit the start commit
   * @param path   the local path within repository
   * @return the submodule resolver that handles submodules inside the specified commit
   */
  public abstract SubmoduleResolver getSubResolver(Commit commit, String path);

  /**
   * Check if the specified directory is a submodule prefix
   *
   * @param path the path to check
   * @return true if the path contains submodules
   */
  public boolean containsSubmodule(String path) {
    ensureConfigLoaded();
    return myConfig.isSubmodulePrefix(path);
  }

  /**
   * @return the current repository
   */
  public Repository getRepository() {
    return myCommit.getRepository();
  }
}
