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

import com.intellij.openapi.diagnostic.Logger;
import com.jcraft.jsch.JSchException;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.serverSide.FileWatchingPropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.OSInfo;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.UnsupportedCharsetException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
 * Utilities for server part of the plugin
 */
public class GitServerUtil {

  public static final long KB = 1024;
  public static final long MB = 1024 * KB;
  public static final long GB = 1024 * MB;

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
      String remoteUrl = remote.toString();
      if (remoteUrl.contains("\n") || remoteUrl.contains("\r"))
        throw new VcsException("Newline in url '" + remoteUrl + "'");
      if (!new File(dir, "config").exists()) {
        r.create(true);
        final StoredConfig config = r.getConfig();
        config.setString("teamcity", null, "remote", remoteUrl);
        config.save();
      } else {
        final StoredConfig config = r.getConfig();
        final String existingRemote = config.getString("teamcity", null, "remote");
        if (existingRemote != null && !remoteUrl.equals(existingRemote)) {
          throw getWrongUrlError(dir, existingRemote, remote);
        } else if (existingRemote == null) {
          config.setString("teamcity", null, "remote", remoteUrl);
          config.save();
        }
      }
      return r;
    } catch (Exception ex) {
      if (ex instanceof NullPointerException)
        LOG.warn("The repository at directory '" + dir + "' cannot be opened or created", ex);
      throw new VcsException("The repository at directory '" + dir + "' cannot be opened or created, reason: " + ex.toString(), ex);
    }
  }

  @NotNull
  static VcsException getWrongUrlError(@NotNull File dir, @NotNull String currentRemote, @NotNull URIish wrongRemote) {
    return new VcsException(
      "The specified directory " + dir + " is already used for another remote " + currentRemote +
      " and cannot be used for others (" + wrongRemote.toString() + "). Please specify the other directory explicitly.");
  }

  private static void ensureRepositoryIsValid(File dir) throws InterruptedException, IOException, ConfigInvalidException {
    File objectsDir = new File(dir, "objects");
    if (objectsDir.exists()) {
      File configFile = new File(dir, "config");
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
        String message = "Wrong username: '" + root.getAuthSettings().getUserName() + "', GitHub expects the 'git' username";
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
    return root.isSsh() && isGithubSshAuthError(te) && !"git".equals(root.getAuthSettings().getUserName());
  }


  private static boolean isGithubSshAuthError(@NotNull TransportException e) {
    Throwable cause = e.getCause();
    return cause instanceof JSchException &&
           ("Auth fail".equals(cause.getMessage()) ||
            "session is down".equals(cause.getMessage()));
  }


  static boolean isAuthError(@NotNull VcsException e) {
    String msg = e.getMessage();
    return msg != null && msg.contains("not authorized");
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
  public static void checkFetchSuccessful(Repository db, FetchResult result) throws VcsException {
    for (TrackingRefUpdate update : result.getTrackingRefUpdates()) {
      String localRefName = update.getLocalName();
      RefUpdate.Result status = update.getResult();
      if (status == RefUpdate.Result.REJECTED || status == RefUpdate.Result.LOCK_FAILURE || status == RefUpdate.Result.IO_FAILURE) {
        if (status == RefUpdate.Result.LOCK_FAILURE) {
          TreeSet<String> caseSensitiveConflicts = new TreeSet<>();
          TreeSet<String> conflicts = new TreeSet<>();
          try {
            OSInfo.OSType os = OSInfo.getOSType();
            if (os == OSInfo.OSType.WINDOWS || os == OSInfo.OSType.MACOSX) {
              Set<String> refNames = db.getRefDatabase().getRefs(RefDatabase.ALL).keySet();
              for (String ref : refNames) {
                if (!localRefName.equals(ref) && localRefName.equalsIgnoreCase(ref))
                  caseSensitiveConflicts.add(ref);
              }
            }
            conflicts.addAll(db.getRefDatabase().getConflictingNames(localRefName));
          } catch (Exception e) {
            //ignore
          }
          String msg;
          if (!conflicts.isEmpty()) {
            msg = "Failed to fetch ref " + localRefName + ": it clashes with " + StringUtil.join(", ", conflicts) +
                  ". Please remove conflicting refs from repository.";
          } else if (!caseSensitiveConflicts.isEmpty()) {
            msg = "Failed to fetch ref " + localRefName + ": on case-insensitive file system it clashes with " +
                  StringUtil.join(", ", caseSensitiveConflicts) +
                  ". Please remove conflicting refs from repository.";
          } else {
            msg = "Fail to update '" + localRefName + "' (" + status.name() + ")";
          }
          throw new VcsException(msg);
        } else {
          throw new VcsException("Fail to update '" + localRefName + "' (" + status.name() + ")");
        }
      }
    }
  }


  static void pruneRemovedBranches(@NotNull ServerPluginConfig config,
                                   @NotNull TransportFactory transportFactory,
                                   @NotNull Transport tn,
                                   @NotNull Repository db,
                                   @NotNull URIish uri,
                                   @NotNull AuthSettings authSettings) throws IOException, VcsException {
    if (config.createNewConnectionForPrune()) {
      Transport transport = null;
      try {
        transport = transportFactory.createTransport(db, uri, authSettings, config.getRepositoryStateTimeoutSeconds());
        pruneRemovedBranches(db, transport);
      } finally {
        if (transport != null)
          transport.close();
      }
    } else {
      pruneRemovedBranches(db, tn);
    }
  }

  /**
   * Removes branches of a bare repository which are not present in a remote repository
   */
  private static void pruneRemovedBranches(@NotNull Repository db, @NotNull Transport tn) {
    FetchConnection conn = null;
    try {
      conn = tn.openFetch();
      Map<String, Ref> remoteRefMap = conn.getRefsMap();
      for (Map.Entry<String, Ref> e : db.getAllRefs().entrySet()) {
        if (!remoteRefMap.containsKey(e.getKey())) {
          try {
            RefUpdate refUpdate = db.getRefDatabase().newUpdate(e.getKey(), false);
            refUpdate.setForceUpdate(true);
            refUpdate.delete();
          } catch (Exception ex) {
            LOG.info("Failed to prune removed ref " + e.getKey(), ex);
            break;
          }
        }
      }
    } catch (IOException e) {
      LOG.info("Failed to list remote refs, continue without pruning removed refs", e);
    } finally {
      if (conn != null)
        conn.close();
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


  /**
   * Read input from System.in until it closed
   *
   * @return input as string
   * @throws IOException
   */
  public static String readInput() throws IOException {
    char[] chars = new char[512];
    StringBuilder sb = new StringBuilder();
    Reader processInput = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
    int count = 0;
    while ((count = processInput.read(chars)) != -1) {
      final String str = new String(chars, 0, count);
      sb.append(str);
    }
    return sb.toString();
  }


  public static void configureInternalProperties(@NotNull final File internalProperties) {
    new TeamCityProperties() {{
      setModel(new FileWatchingPropertiesModel(internalProperties));
    }};
  }


  public static void configureStreamFileThreshold(int thresholdBytes) {
    WindowCacheConfig cfg = new WindowCacheConfig();
    cfg.setStreamFileThreshold(thresholdBytes);
    cfg.install();
  }


  public static void configureExternalProcessLogger(boolean debugEnabled) {
    org.apache.log4j.Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout("[%d] %6p - %30.30c - %m %n")));
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
    org.apache.log4j.Logger.getLogger("org.eclipse.jgit").setLevel(debugEnabled ? Level.DEBUG : Level.OFF);
    org.apache.log4j.Logger.getLogger("jetbrains.buildServer.buildTriggers.vcs.git").setLevel(debugEnabled ? Level.DEBUG : Level.INFO);
  }


  public static void writeAsProperties(@NotNull File f, @NotNull Map<String, String> props) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : props.entrySet()) {
      if (!isEmpty(e.getValue()))
        sb.append(e.getKey()).append("=").append(e.getValue()).append("\n");
    }
    FileUtil.writeFileAndReportErrors(f, sb.toString());
  }


  public static boolean isCannotCreateJvmError(@NotNull ExecResult result) {
    return result.getStderr().contains("Could not create the Java Virtual Machine");
  }

  @Nullable
  public static Long convertMemorySizeToBytes(@Nullable String memory) {
    if (memory == null)
      return null;
    memory = memory.trim();
    if (memory.isEmpty())
      return null;
    int unit = memory.charAt(memory.length() - 1);
    long amount;
    try {
      amount = Long.parseLong(memory.substring(0, memory.length() - 1));
    } catch (NumberFormatException e) {
      return null;
    }
    switch (unit) {
      case 'k':
      case 'K':
        return amount * KB;
      case 'm':
      case 'M':
        return amount * MB;
      case 'g':
      case 'G':
        return amount * GB;
      default:
        return null;
    }
  }


  @Nullable
  public static Long getFreePhysicalMemorySize() {
    try {
      Class.forName("com.sun.management.OperatingSystemMXBean");
    } catch (ClassNotFoundException e) {
      return null;
    }
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
      return ((com.sun.management.OperatingSystemMXBean) osBean).getFreePhysicalMemorySize();
    }
    return null;

  }


  public static boolean isAmazonCodeCommit(@Nullable String host, @NotNull ServerPluginConfig config) {
    if (host == null)
      return false;
    if (host.startsWith("git-codecommit") && host.endsWith("amazonaws.com"))
      return true;
    List<String> amazonHosts = config.getAmazonHosts();
    return amazonHosts.contains(host);
  }


  @NotNull
  public static FetchResult fetch(@NotNull Repository r,
                                  @NotNull URIish url,
                                  @NotNull AuthSettings authSettings,
                                  @NotNull TransportFactory transportFactory,
                                  @NotNull Transport transport,
                                  @NotNull ProgressMonitor progress,
                                  @NotNull Collection<RefSpec> refSpecs,
                                  boolean ignoreMissingRemoteRef) throws NotSupportedException, TransportException, VcsException {
    try {
      return transport.fetch(progress, refSpecs);
    } catch (TransportException e) {
      Throwable cause = e.getCause();
      if (cause instanceof JSchException && "channel is not opened.".equals(cause.getMessage())) {
        return runWithNewTransport(r, url, authSettings, transportFactory, tn -> tn.fetch(progress, refSpecs));
      } else {
        if (ignoreMissingRemoteRef) {
          String missingRef = getMissingRemoteRef(e);
          if (missingRef != null) {
            //exclude spec causing the error
            List<RefSpec> updatedSpecs = refSpecs.stream().filter(spec -> !spec.getSource().equals(missingRef)).collect(Collectors.toList());
            if (updatedSpecs.size() == refSpecs.size())
              throw e;
            return runWithNewTransport(r, url, authSettings, transportFactory, tn ->
              fetch(r, url, authSettings, transportFactory, tn, progress, updatedSpecs, ignoreMissingRemoteRef));
          }
        }
        throw e;
      }
    }
  }


  private interface FetchAction {
    @NotNull
    FetchResult run(@NotNull Transport t) throws NotSupportedException, VcsException, TransportException;
  }

  private static FetchResult runWithNewTransport(@NotNull Repository r,
                                                 @NotNull URIish url,
                                                 @NotNull AuthSettings authSettings,
                                                 @NotNull TransportFactory transportFactory,
                                                 @NotNull FetchAction action) throws NotSupportedException, VcsException, TransportException {
    Transport tn = null;
    try {
      tn = transportFactory.createTransport(r, url, authSettings);
      return action.run(tn);
    } finally {
      if (tn != null)
        tn.close();
    }
  }


  @Nullable
  private static String getMissingRemoteRef(@NotNull TransportException error) {
    String msg = error.getMessage();
    if (msg.startsWith("Remote does not have") && msg.endsWith("available for fetch.")) {
      return msg.substring("Remote does not have".length(), msg.indexOf("available for fetch.")).trim();
    }
    return null;
  }


  @NotNull
  public static String getFullMessage(@NotNull RevCommit commit) {
    try {
      return commit.getFullMessage();
    } catch (UnsupportedCharsetException e) {
      LOG.warn("Cannot parse the " + commit.name() + " commit message due to unknown commit encoding '" + e.getCharsetName() + "'");
      return "Cannot parse commit message due to unknown commit encoding '" + e.getCharsetName() + "'";
    }
  }


  public static PersonIdent getAuthorIdent(@NotNull RevCommit commit) {
    try {
      return commit.getAuthorIdent();
    } catch (UnsupportedCharsetException e) {
      LOG.warn("Cannot parse the " + commit.name() + " commit author due to unknown commit encoding '" + e.getCharsetName() + "'");
      return new PersonIdent("Cannot parse author", "Cannot parse author");
    }
  }
}
