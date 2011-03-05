/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
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
   * Ensures that a bare repository exists at the specified path.
   * If it does not, the directory is attempted to be created.
   *
   * @param dir    the path to the directory to init
   * @param remote the remote URL
   * @return a connection to repository
   * @throws jetbrains.buildServer.vcs.VcsException if the there is a problem with accessing VCS
   */
  public static Repository getRepository(File dir, URIish remote) throws VcsException {
    if (dir.exists() && !dir.isDirectory()) {
      throw new VcsException("The specified path is not a directory: " + dir);
    }
    try {
      Repository r = new RepositoryBuilder().setBare().setGitDir(dir).build();
      if (!new File(dir, "config").exists()) {
        r.create(true);
        final StoredConfig config = r.getConfig();
        config.setString("teamcity", null, "remote", remote.toString());
        config.save();
      } else {
        final StoredConfig config = r.getConfig();
        final String existingRemote = config.getString("teamcity", null, "remote");
        if (existingRemote != null && !remote.toString().equals(existingRemote)) {
          throw new VcsException(
            "The specified directory " + dir + " is already used for another remote " + existingRemote +
            " and cannot be used for others (" + remote.toString() + "). Please specify the other directory explicitly.");
        } else if (existingRemote == null) {
          config.setString("teamcity", null, "remote", remote.toString());
          config.save();
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
  public static String makeVersion(RevCommit c) {
    return GitUtils.makeVersion(c.getId().name(), c.getCommitterIdent().getWhen().getTime());
  }

  public static String getParentVersion(RevCommit commit, String defaultParentVersion) {
    RevCommit[] parents = commit.getParents();
    if (parents.length == 0) {
      return GitUtils.makeVersion(ObjectId.zeroId().name(), 0);
    } else {
      if (commit.getParent(0).getRawBuffer() == null) {
        return defaultParentVersion;
      } else {
        return GitServerUtil.makeVersion(commit.getParents()[0]);
      }
    }
  }

  public static String getUser(Settings s, RevCommit c) {
    return getUser(c.getAuthorIdent(), s);
  }

  private static String getUser(PersonIdent id, Settings s) {
    switch (s.getUsernameStyle()) {
      case NAME:
        return id.getName();
      case EMAIL:
        return id.getEmailAddress();
      case FULL:
        return id.getName() + " <" + id.getEmailAddress() + ">";
      case USERID:
        String email = id.getEmailAddress();
        final int i = email.lastIndexOf("@");
        return email.substring(0, i > 0 ? i : email.length());
      default:
        throw new IllegalStateException("Unsupported username style: " + s.getUsernameStyle());
    }
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

  /**
   * Test if uri contains a common error -- redundant colon after hostname.
   *
   * Example of incorrect uri:
   *
   * ssh://hostname:/path/to/repo.git
   *
   * ':' after hostname is redundant.
   *
   * URIish doesn't throw an exception for such uri in its constructor (see
   * https://bugs.eclipse.org/bugs/show_bug.cgi?id=315571 for explanation why),
   * exception is thrown only on attempt to open transport.
   *
   * @param uri uri to check
   * @return true if uri contains this error
   */
  public static boolean isRedundantColon(URIish uri) {
    return uri.getScheme().equals("ssh") &&
           uri.getHost() == null &&
           uri.getPath().contains(":");
  }

  public static boolean isUnknownHostKeyError(TransportException error) {
    String message = error.getMessage();
    return message != null && message.contains("UnknownHostKey") && message.contains("key fingerprint is");
  }
}
