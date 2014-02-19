/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.jcraft.jsch.JSchException;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

/**
 * Utilities for server part of the plugin
 */
public class GitServerUtil {

  private static Logger LOG = Logger.getInstance(GitServerUtil.class.getName());

  /**
   * Amount of characters displayed for in the display version of revision number
   */
  public static final int DISPLAY_VERSION_AMOUNT = 40;

  /**
   * Ensures that a bare repository exists at the specified path.
   * If it does not, the directory is attempted to be created.
   *
   * @param dir    the path to the directory to init
   * @param remote the remote URL
   * @return a connection to repository
   * @throws VcsException if the there is a problem with accessing VCS
   */
  public static Repository getRepository(@NotNull final File dir, @NotNull final URIish remote) throws VcsException {
    if (dir.exists() && !dir.isDirectory()) {
      throw new VcsException("The specified path is not a directory: " + dir);
    }
    try {
      ensureRepositoryIsValid(dir);
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
      throw new VcsException("The repository at directory '" + dir + "' cannot be opened or created, reason: " + ex.getMessage(), ex);
    }
  }

  private static void ensureRepositoryIsValid(File dir) throws InterruptedException, IOException, ConfigInvalidException {
    File objectsDir = new File(dir, "objects");
    if (objectsDir.exists()) {
      File configFile = new File(dir, "config");
      LOG.debug("Ensure repository at '" + dir.getAbsolutePath() + "' has a valid config file");
      boolean valid = ensureConfigIsValid(configFile);
      if (!valid) {
        LOG.warn("Repository at '" + dir.getAbsolutePath() + "' has invalid config file, try to remove repository");
        if (!FileUtil.delete(dir))
          LOG.warn("Cannot remove repository at '" + dir.getAbsolutePath() + "', operations with such repository most likely will fail");
      }
    }
  }

  private static boolean ensureConfigIsValid(File configLocation) throws InterruptedException, IOException, ConfigInvalidException {
    for (int i = 0; i < 3; i++) {
      FileBasedConfig config = new FileBasedConfig(configLocation, FS.DETECTED);
      config.load();
      if (hasValidFormatVersion(config)) {
        return true;
      } else {
        if (i < 2) {
          LOG.warn("Config " + configLocation.getAbsolutePath() + " has invalid format version, will wait and check again");
          Thread.sleep(2000);
        } else {
          LOG.warn("Config " + configLocation.getAbsolutePath() + " has invalid format version");
        }
      }
    }
    return false;
  }


  private static boolean hasValidFormatVersion(Config config) {
    final String repositoryFormatVersion = config.getString(ConfigConstants.CONFIG_CORE_SECTION, null,
                                                            ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION);
    return "0".equals(repositoryFormatVersion);
  }

  public static String getUser(GitVcsRoot root, RevCommit c) {
    return getUser(root, c.getAuthorIdent());
  }

  public static String getUser(GitVcsRoot root, PersonIdent id) {
    switch (root.getUsernameStyle()) {
      case NAME:
        return id.getName();
      case EMAIL:
        return id.getEmailAddress();
      case FULL:
        return getFullUserName(id);
      case USERID:
        String email = id.getEmailAddress();
        final int i = email.lastIndexOf("@");
        return email.substring(0, i > 0 ? i : email.length());
      default:
        throw new IllegalStateException("Unsupported username style: " + root.getUsernameStyle());
    }
  }

  public static String getFullUserName(@NotNull final PersonIdent id) {
    return id.getName() + " <" + id.getEmailAddress() + ">";
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


  public static Exception friendlyTransportException(@NotNull TransportException te, @NotNull GitVcsRoot root) {
    if (isUnknownHostKeyError(te)) {
      String originalMessage = te.getMessage();
      String message = originalMessage + ". Add this host to a known hosts database or check option 'Ignore Known Hosts Database'.";
      return new VcsException(message, te);
    }

    if (root.isOnGithub()) {
      if (isWrongGithubUsername(te, root)) {
        String message = "Wrong username: '" + root.getAuthSettings().getUserName() + "', github expects a username 'git'";
        return new VcsException(message, te);
      }
      if (root.isHttp() && !root.getRepositoryFetchURL().getPath().endsWith(".git") &&
          te.getMessage().contains("service=git-upload-pack not found")) {
        String url = root.getRepositoryFetchURL().toString();
        String message = "Url \"" + url + "\" might be incorrect, try using \"" + url + ".git\"";
        return new VcsException(message, te);
      }
    }

    return te;
  }


  private static boolean isWrongGithubUsername(@NotNull TransportException te, @NotNull GitVcsRoot root) {
    return root.isSsh() && isAuthError(te) && !"git".equals(root.getAuthSettings().getUserName());
  }


  private static boolean isAuthError(@NotNull TransportException e) {
    Throwable cause = e.getCause();
    return cause instanceof JSchException &&
           ("Auth fail".equals(cause.getMessage()) ||
            "session is down".equals(cause.getMessage()));
  }


  @NotNull
  public static NotSupportedException friendlyNotSupportedException(@NotNull GitVcsRoot root, @NotNull NotSupportedException nse)  {
    URIish fetchURI = root.getRepositoryFetchURL();
    if (isRedundantColon(fetchURI)) {
      //url with username looks like ssh://username/hostname:/path/to/repo - it will
      //confuse user even further, so show url without user name
      return new NotSupportedException(MessageFormat.format(JGitText.get().URINotSupported, root.getProperty(Constants.FETCH_URL)) +
                                      ". Make sure you don't have a colon after the host name.");
    } else {
      return nse;
    }
  }


  private static boolean isUnknownHostKeyError(TransportException error) {
    String message = error.getMessage();
    return message != null && message.contains("UnknownHostKey") && message.contains("key fingerprint is");
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
  private static boolean isRedundantColon(URIish uri) {
    return "ssh".equals(uri.getScheme()) &&
           uri.getHost() == null &&
           uri.getPath() != null && uri.getPath().contains(":");
  }


  /**
   * Check all refs successfully updated, throws exception if they are not
   * @param result fetch result
   * @throws VcsException if any ref was not successfully updated
   */
  public static void checkFetchSuccessful(FetchResult result) throws VcsException {
    for (TrackingRefUpdate update : result.getTrackingRefUpdates()) {
      RefUpdate.Result status = update.getResult();
      if (status == RefUpdate.Result.REJECTED || status == RefUpdate.Result.LOCK_FAILURE || status == RefUpdate.Result.IO_FAILURE) {
        throw new VcsException("Fail to update '" + update.getLocalName() + "' (" + status.name() + ").");
      }
    }
  }


  public static boolean isCloned(@NotNull Repository db) throws VcsException, IOException {
    if (!db.getObjectDatabase().exists())
      return false;
    ObjectReader reader = db.getObjectDatabase().newReader();
    try {
      for (Ref ref : db.getRefDatabase().getRefs(RefDatabase.ALL).values()) {
        if (reader.has(ref.getObjectId()))
          return true;
      }
    } finally {
      reader.release();
    }
    return false;
  }
}
