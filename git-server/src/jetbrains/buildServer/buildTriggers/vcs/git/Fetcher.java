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

import jetbrains.buildServer.util.DiagnosticUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsUtil;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Method main of this class is supposed to be run in separate process to avoid OutOfMemoryExceptions in server's process
 *
 * @author dmitry.neverov
 */
public class Fetcher {

  public static void main(String[] args) throws IOException, VcsException, URISyntaxException {
    boolean debug = false;
    ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
    final long start = System.currentTimeMillis();
    try {
      Map<String, String> properties = VcsUtil.stringToProperties(GitServerUtil.readInput());
      String threadDumpFilePath = properties.remove(Constants.THREAD_DUMP_FILE);
      String repositoryPath = properties.remove(Constants.REPOSITORY_DIR_PROPERTY_NAME);
      debug = "true".equals(properties.remove(Constants.VCS_DEBUG_ENABLED));

      GitServerUtil.configureExternalProcessLogger(debug);

      String internalPropsFile = properties.remove(Constants.FETCHER_INTERNAL_PROPERTIES_FILE);
      GitServerUtil.configureInternalProperties(new File(internalPropsFile));

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      FetchProgressMonitor progress = new FetchProgressMonitor(new PrintStream(output));
      exec.scheduleAtFixedRate(new Monitoring(threadDumpFilePath, output), 10, 10, TimeUnit.SECONDS);
      fetch(new File(repositoryPath), properties, progress);

      if (System.currentTimeMillis() - start <= new PluginConfigImpl().getMonitoringFileThresholdMillis()) {
        FileUtil.delete(new File(threadDumpFilePath));
      }
    } catch (Throwable t) {
      if (debug || isImportant(t)) {
        t.printStackTrace(System.err);
      } else {
        System.err.println(t.getMessage());
      }
      System.exit(1);
    } finally {
      exec.shutdown();
    }
  }

  /**
   * Do fetch in directory <code>repositoryDir</code> with vcsRootProperties from <code>vcsRootProperties</code>
   *
   * @param repositoryDir     directory where run fetch
   * @param vcsRootProperties properties of vcsRoot
   * @throws IOException
   * @throws VcsException
   * @throws URISyntaxException
   */
  private static void fetch(@NotNull File repositoryDir,
                            @NotNull Map<String, String> vcsRootProperties,
                            @NotNull ProgressMonitor progressMonitor) throws IOException, VcsException, URISyntaxException {
    final String fetchUrl = vcsRootProperties.get(Constants.FETCH_URL);
    final String refspecs = vcsRootProperties.get(Constants.REFSPEC);
    final String trustedCertificatesDir = vcsRootProperties.get(Constants.GIT_TRUST_STORE_PROVIDER);
    AuthSettings auth = new AuthSettings(vcsRootProperties);
    PluginConfigImpl config = new PluginConfigImpl();

    GitServerUtil.configureStreamFileThreshold(Integer.MAX_VALUE);

    TransportFactory transportFactory = new TransportFactoryImpl(config, new EmptyVcsRootSshKeyManager(),
                                                                 new GitTrustStoreProviderStatic(trustedCertificatesDir));
    Transport tn = null;
    try {
      Repository repository = new RepositoryBuilder().setBare().setGitDir(repositoryDir).build();
      workaroundRacyGit();
      tn = transportFactory.createTransport(repository, new URIish(fetchUrl), auth);
      try {
        pruneRemovedBranches(config, repository, transportFactory, tn, new URIish(fetchUrl), auth);
      } catch (Exception e) {
        System.err.println("Error while pruning removed branches: " + e.getMessage());
        e.printStackTrace(System.err);
      }
      FetchResult result = GitServerUtil.fetch(repository, new URIish(fetchUrl), auth, transportFactory, tn, progressMonitor,
                                               parseRefspecs(refspecs), config.ignoreMissingRemoteRef());
      GitServerUtil.checkFetchSuccessful(repository, result);
      logFetchResults(result);
    } finally {
      if (tn != null)
        tn.close();
    }
  }

  private static void pruneRemovedBranches(@NotNull ServerPluginConfig config,
                                           @NotNull Repository db,
                                           @NotNull TransportFactory transportFactory,
                                           @NotNull Transport tn,
                                           @NotNull URIish uri,
                                           @NotNull AuthSettings authSettings) throws IOException, VcsException {
    GitServerUtil.pruneRemovedBranches(config, transportFactory, tn, db, uri, authSettings);
  }

  private static void logFetchResults(@NotNull FetchResult result) {
    for (TrackingRefUpdate update : result.getTrackingRefUpdates()) {
      StringBuilder msg = new StringBuilder();
      msg.append("update ref remote name: ").append(update.getRemoteName())
        .append(", local name: ").append(update.getLocalName())
        .append(", old object id: ").append(update.getOldObjectId().name())
        .append(", new object id: ").append(update.getNewObjectId().name())
        .append(", result: ").append(update.getResult());
      System.out.println(msg);
    }
    String additionalMsgs = result.getMessages();
    if (additionalMsgs.length() > 0) {
      System.out.println("Remote process messages: " + additionalMsgs);
    }
  }

  private static boolean isImportant(Throwable t) {
    return t instanceof NullPointerException ||
           t instanceof Error ||
           t instanceof InterruptedException ||
           t instanceof InterruptedIOException;
  }

  /**
   * Fetch could be so fast that even though it downloads some new packs
   * a timestamp of objects/packs dir is not changed (at least on linux).
   * If timestamp of that dir is not changed from the last read, jgit assumes
   * there is nothing new there and could not find object even if it already
   * exists in repository. This method sleeps for 1 second, so subsequent
   * write to objects/pack dir will change its timestamp.
   */
  private static void workaroundRacyGit() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static Collection<RefSpec> parseRefspecs(String refspecs) {
    String[] specs = refspecs.split(Constants.RECORD_SEPARATOR);
    List<RefSpec> result = new ArrayList<RefSpec>();
    for (String spec : specs) {
      result.add(new RefSpec(spec).setForceUpdate(true));
    }
    return result;
  }

  private static class Monitoring implements Runnable {

    private final File myFile;
    private final ByteArrayOutputStream myGitOutput;

    Monitoring(@NotNull String threadDumpFilePath, @NotNull ByteArrayOutputStream gitOutput) {
      myFile = new File(threadDumpFilePath);
      myGitOutput = gitOutput;
    }

    public void run() {
      String threadDump = DiagnosticUtil.threadDumpToString();
      String gitProgress = myGitOutput.toString();
      FileUtil.writeFile(myFile, threadDump + "\ngit progress:\n" + gitProgress);
    }
  }
}
