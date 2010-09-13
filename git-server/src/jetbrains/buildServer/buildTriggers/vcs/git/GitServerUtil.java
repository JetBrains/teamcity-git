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

import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.URIish;

import java.io.File;

/**
 * Utilities for server part of the plugin
 */
public class GitServerUtil {
  /**
   * Amount of characters displayed for in the display version of revision number
   */
  public static final int DISPLAY_VERSION_AMOUNT = 40;
  /**
   * User name for the system user
   */
  public static final String SYSTEM_USER = "system@git-plugin.teamcity";
  /**
   * Max size of cached file
   */
  public static final int MAX_CACHED_FILE = 16 * 1024;

  /**
   * Ensures that a bare repository exists at the specified path.
   * If it does not, the directory is attempted to be created.
   *
   * @param dir    the path to the directory to init
   * @param remote the remote URL
   * @return a connection to repository
   * @throws jetbrains.buildServer.vcs.VcsException if the there is a problem with accessing VCS
   */
  public static Repository getRepository(File dir, URIish remote) throws VcsException {
    WindowCacheConfig cfg = new WindowCacheConfig();
    cfg.setDeltaBaseCacheLimit(MAX_CACHED_FILE);
    WindowCache.reconfigure(cfg);
    if (dir.exists() && !dir.isDirectory()) {
      throw new VcsException("The specified path is not a directory: " + dir);
    }
    try {
      Repository r = new Repository(dir);
      if (!new File(dir, "config").exists()) {
        r.create(true);
        final RepositoryConfig config = r.getConfig();
        config.setString("teamcity", null, "remote", remote.toString());
        config.save();
      } else {
        final RepositoryConfig config = r.getConfig();
        final String existingRemote = config.getString("teamcity", null, "remote");
        if (existingRemote != null && !remote.toString().equals(existingRemote)) {
          throw new VcsException(
            "The specified directory " + dir + " is already used for another remote " + existingRemote +
            " and cannot be used for others (" + remote.toString() + "). Please specify the other directory explicitly.");
        }
      }
      return r;
    } catch (Exception ex) {
      throw new VcsException("The repository at " + dir + " cannot be opened or created: " + ex, ex);
    }
  }

  /**
   * Make version from commit object
   *
   * @param c the commit object
   * @return the version string
   */
  public static String makeVersion(Commit c) {
    return GitUtils.makeVersion(c.getCommitId().name(), c.getCommitter().getWhen().getTime());
  }

  /**
   * Get user for the commit
   *
   * @param s the vcs root settings
   * @param c the commit
   * @return the user name
   */
  public static String getUser(Settings s, Commit c) {
    final PersonIdent a = c.getAuthor();
    switch (s.getUsernameStyle()) {
      case NAME:
        return a.getName();
      case EMAIL:
        return a.getEmailAddress();
      case FULL:
        return a.getName() + " <" + a.getEmailAddress() + ">";
      case USERID:
        String email = a.getEmailAddress();
        final int i = email.lastIndexOf("@");
        return email.substring(0, i > 0 ? i : email.length());
      default:
        throw new IllegalStateException("Unsupported username style: " + s.getUsernameStyle());
    }
  }

  /**
   * Create display version for the commit
   *
   * @param c the commit to examine
   * @return the display version
   */
  public static String displayVersion(Commit c) {
    return displayVersion(c.getCommitId().name());
  }

  /**
   * Create display version for the commit
   *
   * @param version the version to examine
   * @return the display version
   */
  public static String displayVersion(String version) {
    return version.substring(0, DISPLAY_VERSION_AMOUNT);
  }
}
