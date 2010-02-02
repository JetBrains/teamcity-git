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

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.Settings;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

/**
 * The resolver for submodules that uses TeamCity repository mapping.
 */
public class TeamCitySubmoduleResolver extends SubmoduleResolver {
  /**
   * logger instance
   */
  private static Logger LOG = Logger.getInstance(TeamCitySubmoduleResolver.class.getName());
  /**
   * Base path within root for the resolver
   */
  final String myBasePath;
  /**
   * The settings object
   */
  final Settings mySettings;
  /**
   * The vcs support
   */
  private final GitVcsSupport myVcs;
  /**
   * Repositories created for submodules
   */
  private final Map<String, Repository> mySubmoduleRepositories;

  /**
   * The resolver constructor
   *
   * @param submoduleRepositories the collection to accumulate submodule repositories
   * @param vcs                   the Git vcs service
   * @param settings              the settings object
   * @param commit                the commit this resolves handles
   */
  public TeamCitySubmoduleResolver(Map<String, Repository> submoduleRepositories, GitVcsSupport vcs, Settings settings, Commit commit) {
    this(submoduleRepositories, vcs, settings, "", commit);
  }

  /**
   * The resolver constructor
   *
   * @param submoduleRepositories the collection to accumulate submodule repositories
   * @param vcs                   the Git vcs service
   * @param settings              the settings object
   * @param basePath              the base path
   * @param commit                the commit this resolves handles
   */
  private TeamCitySubmoduleResolver(Map<String, Repository> submoduleRepositories,
                                    GitVcsSupport vcs,
                                    Settings settings,
                                    String basePath,
                                    Commit commit) {
    super(commit);
    mySubmoduleRepositories = submoduleRepositories;
    myBasePath = basePath;
    mySettings = settings;
    myVcs = vcs;
  }

  /**
   * {@inheritDoc}
   */
  protected Repository resolveRepository(String path, String url) throws IOException {
    String overrideUrl = mySettings.getSubmoduleUrl(path);
    if (overrideUrl != null) {
      url = overrideUrl;
    }
    try {
      if (url.startsWith(".")) {
        String baseUrl = myCommit.getRepository().getConfig().getString("teamcity", null, "remote");
        URIish u = new URIish(baseUrl);
        String newPath = u.getPath();
        if(newPath.length() == 0) {
          newPath = url;
        } else {
          newPath = GitUtils.normalizePath(newPath + '/'+url);
        }
        url = u.setPass(newPath).toPrivateString();
      }
      String dir = mySettings.getSubmodulePath(path, url);
      if (mySubmoduleRepositories.containsKey(dir)) {
        return mySubmoduleRepositories.get(dir);
      }
      final URIish uri = new URIish(url);
      final Repository r = GitUtils.getRepository(new File(dir), uri);
      mySubmoduleRepositories.put(dir, r);
      final Transport tn = myVcs.openTransport(mySettings, r, uri);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Fetching submodule " + path + " data for " + mySettings.debugInfo());
      }
      try {
        String refName = GitUtils.branchRef("*");
        RefSpec spec = new RefSpec("+" + refName + ":" + refName);
        tn.fetch(NullProgressMonitor.INSTANCE, Collections.singletonList(spec));
      } finally {
        tn.close();
      }
      return r;
    } catch (VcsException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException)e.getCause();
      }
      final IOException ex = new IOException(e.getMessage());
      ex.initCause(e);
      throw ex;
    } catch (URISyntaxException e) {
      final IOException ex = new IOException(e.getMessage());
      ex.initCause(e);
      throw ex;
    }
  }

  /**
   * {@inheritDoc}
   */
  public SubmoduleResolver getSubResolver(Commit commit, String path) {
    return new TeamCitySubmoduleResolver(mySubmoduleRepositories, myVcs, mySettings, fullPath(path), commit);
  }

  /**
   * Get full path using from local path
   *
   * @param path the path to examine
   * @return the full including the base path
   */
  private String fullPath(String path) {
    return myBasePath.length() == 0 ? path : myBasePath + "/" + path;
  }
}
