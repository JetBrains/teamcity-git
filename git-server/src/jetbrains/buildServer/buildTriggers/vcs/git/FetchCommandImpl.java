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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsUtil;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
* @author dmitry.neverov
*/
public class FetchCommandImpl implements FetchCommand {

  private static Logger LOG = Logger.getInstance(FetchCommandImpl.class.getName());
  private static Logger PERFORMANCE_LOG = Logger.getInstance(FetchCommandImpl.class.getName() + ".Performance");

  private final ServerPluginConfig myConfig;
  private final TransportFactory myTransportFactory;
  private final FetcherProperties myFetcherProperties;
  private final VcsRootSshKeyManager mySshKeyManager;

  public FetchCommandImpl(@NotNull ServerPluginConfig config,
                          @NotNull TransportFactory transportFactory,
                          @NotNull FetcherProperties fetcherProperties,
                          @NotNull VcsRootSshKeyManager sshKeyManager) {
    myConfig = config;
    myTransportFactory = transportFactory;
    myFetcherProperties = fetcherProperties;
    mySshKeyManager = sshKeyManager;
  }


  public void fetch(@NotNull final Repository db, @NotNull final URIish fetchURI,
                    @NotNull final Collection<RefSpec> refspecs, @NotNull final AuthSettings auth) throws NotSupportedException, VcsException, TransportException {
    unlockRefs(db);
    if (myConfig.isSeparateProcessForFetch()) {
      fetchInSeparateProcess(db, auth, fetchURI, refspecs);
    } else {
      fetchInSameProcess(db, auth, fetchURI, refspecs);
    }
  }


  private void unlockRefs(Repository db) throws VcsException{
    try {
      Map<String, Ref> refMap = db.getRefDatabase().getRefs(org.eclipse.jgit.lib.Constants.R_HEADS);
      for (Ref ref : refMap.values()) {
        unlockRef(db, ref);
      }
    } catch (Exception e) {
      throw new VcsException(e);
    }
  }


  private void unlockRef(Repository db, Ref ref) throws IOException, InterruptedException {
    File refFile = new File(db.getDirectory(), ref.getName());
    File refLockFile = new File(db.getDirectory(), ref.getName() + ".lock");
    LockFile lock = new LockFile(refFile, FS.DETECTED);
    try {
      if (!lock.lock()) {
        LOG.warn("Cannot lock the ref " + ref.getName() + ", will wait and try again");
        Thread.sleep(5000);
        if (lock.lock()) {
          LOG.warn("Successfully lock the ref " + ref.getName());
        } else {
          if (FileUtil.delete(refLockFile)) {
            LOG.warn("Remove ref lock " + refLockFile.getAbsolutePath());
          } else {
            LOG.warn("Cannot remove ref lock " + refLockFile.getAbsolutePath() + ", fetch will probably fail. Please remove lock manually.");
          }
        }
      }
    } finally {
      lock.unlock();
    }
  }


  private void fetchInSeparateProcess(@NotNull final Repository repository, @NotNull final AuthSettings settings,
                                      @NotNull final URIish uri, @NotNull final Collection<RefSpec> specs) throws VcsException {
    final long fetchStart = System.currentTimeMillis();
    final String debugInfo = getDebugInfo(repository, uri, specs);

    File gitPropertiesFile = null;
    File teamcityPrivateKey = null;
    try {
      GeneralCommandLine cl = createFetcherCommandLine(repository, uri);
      if (LOG.isDebugEnabled())
        LOG.debug("Start fetch process for " + debugInfo);

      File threadDump = getThreadDumpFile(repository);
      gitPropertiesFile = myFetcherProperties.getPropertiesFile();
      FetcherEventHandler processEventHandler = new FetcherEventHandler(debugInfo);
      teamcityPrivateKey = getTeamCityPrivateKey(settings);
      AuthSettings preparedSettings = settings;
      if (teamcityPrivateKey != null) {
        Map<String, String> properties = settings.toMap();
        properties.put(Constants.AUTH_METHOD, AuthenticationMethod.PRIVATE_KEY_FILE.name());
        properties.put(Constants.PRIVATE_KEY_PATH, teamcityPrivateKey.getAbsolutePath());
        preparedSettings = new AuthSettings(properties, settings.getRoot());
      }

      byte[] fetchProcessInput = getFetchProcessInputBytes(preparedSettings, repository.getDirectory(), uri, specs, threadDump, gitPropertiesFile);
      ExecResult result = SimpleCommandLineProcessRunner.runCommand(cl, fetchProcessInput, processEventHandler);

      if (PERFORMANCE_LOG.isDebugEnabled())
        PERFORMANCE_LOG.debug("[fetch in separate process] root=" + debugInfo + ", took " + (System.currentTimeMillis() - fetchStart) + "ms");

      VcsException commandError = CommandLineUtil.getCommandLineError("git fetch", result);
      if (commandError != null) {
        if (isOutOfMemoryError(result))
          LOG.warn("There is not enough memory for git fetch, teamcity.git.fetch.process.max.memory=" + myConfig.getFetchProcessMaxMemory() + ", try to increase it.");
        if (isTimeout(result))
          logTimeout(debugInfo, threadDump);
        clean(repository);
        throw commandError;
      }
      if (result.getStderr().length() > 0) {
        LOG.warn("Error output produced by git fetch:\n" + result.getStderr());
      }

      LOG.debug("Fetch process output:\n" + result.getStdout());
    } finally {
      if (teamcityPrivateKey != null)
        FileUtil.delete(teamcityPrivateKey);
      if (gitPropertiesFile != null)
        FileUtil.delete(gitPropertiesFile);
    }
  }

  private File getTeamCityPrivateKey(@NotNull AuthSettings authSettings) throws VcsException {
    if (authSettings.getAuthMethod() != AuthenticationMethod.TEAMCITY_SSH_KEY)
      return null;

    String keyId = authSettings.getTeamCitySshKeyId();
    if (keyId == null)
      return null;

    VcsRoot root = authSettings.getRoot();
    if (root == null)
      return null;

    TeamCitySshKey privateKey = mySshKeyManager.getKey(root);
    if (privateKey == null)
      return null;

    try {
      File privateKeyFile = FileUtil.createTempFile("private", "key");
      FileUtil.writeToFile(privateKeyFile, privateKey.getPrivateKey());
      return privateKeyFile;
    } catch (IOException e) {
      throw new VcsException(e);
    }
  }

  private void logTimeout(@NotNull String debugInfo, @NotNull File threadDump) {
    StringBuilder message = new StringBuilder();
    message.append("Fetch in root ").append(debugInfo)
      .append(" took more than ")
      .append(myConfig.getFetchTimeout())
      .append(" second(s), try increasing a timeout using the " + PluginConfigImpl.TEAMCITY_GIT_IDLE_TIMEOUT_SECONDS + " property.");
    if (threadDump.exists())
      message.append(" Fetch progress details can be found in ").append(threadDump.getAbsolutePath());
    LOG.warn(message.toString());
  }

  private File getThreadDumpFile(@NotNull Repository repository) {
    File threadDumpsDir = getMonitoringDir(repository);
    threadDumpsDir.mkdirs();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
    return new File(threadDumpsDir, sdf.format(Dates.now()) + ".txt");
  }

  private File getMonitoringDir(@NotNull Repository repository) {
    return new File(repository.getDirectory(), myConfig.getMonitoringDirName());
  }

  private GeneralCommandLine createFetcherCommandLine(@NotNull final Repository repository, @NotNull final URIish uri) {
    GeneralCommandLine cl = new GeneralCommandLine();
    cl.setWorkingDirectory(repository.getDirectory());
    cl.setExePath(myConfig.getFetchProcessJavaPath());
    cl.addParameters(myConfig.getProxySettingsForSeparateProcess());

    String trustStore = System.getProperty("javax.net.ssl.trustStore");
    String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
    if (trustStore != null)
      cl.addParameter("-Djavax.net.ssl.trustStore=" + trustStore);
    if (trustStorePassword != null)
      cl.addParameter("-Djavax.net.ssl.trustStorePassword=" + trustStorePassword);

    cl.addParameters("-Xmx" + myConfig.getFetchProcessMaxMemory(),
                     "-cp", myConfig.getFetchClasspath(),
                     myConfig.getFetcherClassName(),
                     uri.toString());//last parameter is not used in Fetcher, but is useful to distinguish fetch processes
    return cl;
  }


  private void fetchInSameProcess(@NotNull final Repository db, @NotNull final AuthSettings auth,
                                  @NotNull final URIish uri, @NotNull final Collection<RefSpec> refSpecs) throws NotSupportedException, VcsException, TransportException {
    final String debugInfo = getDebugInfo(db, uri, refSpecs);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Fetch in server process: " + debugInfo);
    }
    final long fetchStart = System.currentTimeMillis();
    final Transport tn = myTransportFactory.createTransport(db, uri, auth);
    try {
      try {
        GitServerUtil.pruneRemovedBranches(db, tn);
      } catch (Exception e) {
        LOG.error("Error while pruning removed branches", e);
      }
      FetchResult result = GitServerUtil.fetch(db, uri, auth, myTransportFactory, tn, NullProgressMonitor.INSTANCE, refSpecs);
      GitServerUtil.checkFetchSuccessful(result);
    } catch (OutOfMemoryError oom) {
      LOG.warn("There is not enough memory for git fetch, try to run fetch in a separate process.");
      clean(db);
    } finally {
      clean(db);
      tn.close();
      if (PERFORMANCE_LOG.isDebugEnabled()) {
        PERFORMANCE_LOG.debug("[fetch in server process] root=" + debugInfo + ", took " + (System.currentTimeMillis() - fetchStart) + "ms");
      }
    }
  }

  private String getDebugInfo(Repository db, URIish uri, Collection<RefSpec> refSpecs) {
    StringBuilder sb = new StringBuilder();
    for (RefSpec spec : refSpecs) {
      sb.append(spec).append(" ");
    }
    return " (" + (db.getDirectory() != null? db.getDirectory().getAbsolutePath() + ", ":"") + uri.toString() + "#" + sb.toString() + ")";
  }


  private boolean isOutOfMemoryError(@NotNull ExecResult result) {
    return result.getStderr().contains("java.lang.OutOfMemoryError");
  }

  private boolean isTimeout(@NotNull ExecResult result) {
    //noinspection ThrowableResultOfMethodCallIgnored
    final Throwable exception = result.getException();
    return exception instanceof InterruptedException &&
           "Timeout exception".equals(exception.getMessage());
  }

  /**
   * Clean out garbage in case of errors
   * @param db repository
   */
  private void clean(Repository db) {
    //When jgit loads new pack into repository, it first writes it to file
    //incoming_xxx.pack. When it tries to open such pack we can run out of memory.
    //In this case incoming_xxx.pack files will waste disk space.
    //See TW-13450 for details
    File objectsDir = ((FileRepository) db).getObjectsDirectory();
    for (File f : objectsDir.listFiles()) {
      if (f.isFile() && f.getName().startsWith("incoming_") && f.getName().endsWith(".pack")) {
        FileUtil.delete(f);
      }
    }
  }


  private class FetcherEventHandler implements SimpleCommandLineProcessRunner.ProcessRunCallback {
    private final String myRepositoryDebugInfo;

    FetcherEventHandler(@NotNull final String repositoryDebugInfo) {
      myRepositoryDebugInfo = repositoryDebugInfo;
    }

    public void onProcessStarted(Process ps) {
      if (LOG.isDebugEnabled())
        LOG.debug("Fetch process for " + myRepositoryDebugInfo + " started");
    }

    public void onProcessFinished(Process ps) {
      if (LOG.isDebugEnabled())
        LOG.debug("Fetch process for " + myRepositoryDebugInfo + " finished");
    }

    public Integer getOutputIdleSecondsTimeout() {
      return myConfig.getFetchTimeout();
    }

    public Integer getMaxAcceptedOutputSize() {
      return null;
    }
  }

  private byte[] getFetchProcessInputBytes(@NotNull AuthSettings authSettings,
                                           @NotNull File repositoryDir,
                                           @NotNull URIish uri,
                                           @NotNull Collection<RefSpec> specs,
                                           @NotNull File threadDump,
                                           @NotNull File gitProperties) throws VcsException {
    try {
      Map<String, String> properties = new HashMap<String, String>(authSettings.toMap());
      properties.put(Constants.REPOSITORY_DIR_PROPERTY_NAME, repositoryDir.getCanonicalPath());
      properties.put(Constants.FETCH_URL, uri.toString());
      properties.put(Constants.REFSPEC, serializeSpecs(specs));
      properties.put(Constants.VCS_DEBUG_ENABLED, String.valueOf(Loggers.VCS.isDebugEnabled()));
      properties.put(Constants.THREAD_DUMP_FILE, threadDump.getAbsolutePath());
      properties.put(Constants.FETCHER_INTERNAL_PROPERTIES_FILE, gitProperties.getAbsolutePath());
      return VcsUtil.propertiesToStringSecure(properties).getBytes("UTF-8");
    } catch (IOException e) {
      throw new VcsException("Error while generating fetch process input", e);
    }
  }

  private String serializeSpecs(@NotNull final Collection<RefSpec> specs) {
    StringBuilder sb = new StringBuilder();
    Iterator<RefSpec> iter = specs.iterator();
    while (iter.hasNext()) {
      RefSpec spec = iter.next();
      sb.append(spec);
      if (iter.hasNext())
        sb.append(Constants.RECORD_SEPARATOR);
    }
    return sb.toString();
  }
}
