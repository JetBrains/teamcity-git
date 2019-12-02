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

import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.util.DiagnosticUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsUtil;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.jetbrains.annotations.NotNull;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.io.*;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.MB;

/**
 * Method main of this class is supposed to be run in separate process to avoid OutOfMemoryExceptions in server's process
 *
 * @author dmitry.neverov
 */
public class Fetcher {

  public static void main(String[] args) throws IOException, VcsException, URISyntaxException {
    boolean debug = false;
    ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
    GcListener gcListener = null;
    final long start = System.currentTimeMillis();
    try {
      Map<String, String> properties = VcsUtil.stringToProperties(GitServerUtil.readInput());
      String threadDumpFilePath = properties.remove(Constants.THREAD_DUMP_FILE);
      String gcDumpFilePath = properties.remove(Constants.GC_DUMP_FILE);
      String repositoryPath = properties.remove(Constants.REPOSITORY_DIR_PROPERTY_NAME);
      debug = "true".equals(properties.remove(Constants.VCS_DEBUG_ENABLED));

      GitServerUtil.configureExternalProcessLogger(debug);

      String internalPropsFile = properties.remove(Constants.FETCHER_INTERNAL_PROPERTIES_FILE);
      GitServerUtil.configureInternalProperties(new File(internalPropsFile));

      ByteArrayOutputStream gitOutput = new ByteArrayOutputStream();
      FetchProgressMonitor progress = new FetchProgressMonitor(new PrintStream(gitOutput));
      gcListener = new GcListener(gcDumpFilePath);
      gcListener.startListen();
      exec.scheduleAtFixedRate(new Monitoring(threadDumpFilePath, gitOutput), 10, 10, TimeUnit.SECONDS);
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
    AuthSettings auth = new AuthSettings(vcsRootProperties, new URIishHelperImpl());
    PluginConfigImpl config = new PluginConfigImpl();

    GitServerUtil.setupMemoryMappedIndexReading();
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
      logFetchResults(GitServerUtil.fetchAndCheckResults(repository, new URIish(fetchUrl), auth, transportFactory, tn, progressMonitor,
                                                         parseRefspecs(refspecs), config.ignoreMissingRemoteRef()));
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
      try {
        String threadDump = DiagnosticUtil.threadDumpToString();
        String gitProgress = myGitOutput.toString();
        long memoryUsage = memoryUsage();
        FileUtil.writeFile(myFile, threadDump + "\ngit progress:\n" + gitProgress + "\nmemory usage (MB):\n" + memoryUsage, "UTF-8");
      } catch (IOException e) {
        System.err.println("Exception while persisting thread dump to " + myFile.getAbsolutePath() + "\n" + e);
      }
    }

    private long memoryUsage() {
      return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MB;
    }
  }

  private static class GcListener implements NotificationListener {
    private static Method ourGarbageCollectionNotificationInfo_from = null;
    private static Method ourGarbageCollectionNotificationInfo_getGcInfo = null;
    private static Method ourGcInfo_getMemoryUsageAfterGc = null;
    private static Method ourGcInfo_getMemoryUsageBeforeGc = null;
    private static Method ourGcInfo_getGcDuration = null;
    private static boolean isGcEventListenerInitialized = false;

    static {
      try {
        final Class<?> GarbageCollectionNotificationInfoClass = Class.forName("com.sun.management.GarbageCollectionNotificationInfo");
        ourGarbageCollectionNotificationInfo_from = GarbageCollectionNotificationInfoClass.getMethod("from", CompositeData.class);
        ourGarbageCollectionNotificationInfo_getGcInfo = GarbageCollectionNotificationInfoClass.getMethod("getGcInfo");
        ourGcInfo_getMemoryUsageAfterGc = Class.forName("com.sun.management.GcInfo").getMethod("getMemoryUsageAfterGc");
        ourGcInfo_getMemoryUsageBeforeGc = Class.forName("com.sun.management.GcInfo").getMethod("getMemoryUsageBeforeGc");
        ourGcInfo_getGcDuration = Class.forName("com.sun.management.GcInfo").getMethod("getDuration");
        isGcEventListenerInitialized = true;
      } catch (ClassNotFoundException ignore) {
        System.err.println("Cannot initialize GC listener: class not found");
      } catch (Throwable t) {
        System.err.println("Cannot initialize GC listener \n" + t);
      }
    }

    @NotNull
    private final File myGcDumpFile;

    private GcListener(@NotNull final String gcDumpFilePath) {
      myGcDumpFile = new File(gcDumpFilePath);
    }

    @SuppressWarnings("unchecked")
    private Pair<Long, Long> getGcMemoryDiff(final Object gcInfo) throws IllegalAccessException, InvocationTargetException {
      final Map<String, MemoryUsage> before = (Map<String, MemoryUsage>)ourGcInfo_getMemoryUsageBeforeGc.invoke(gcInfo);
      final Map<String, MemoryUsage> after = (Map<String, MemoryUsage>)ourGcInfo_getMemoryUsageAfterGc.invoke(gcInfo);
      final Set<String> names = new HashSet<>(before.keySet());
      names.addAll(after.keySet());

      long bTotal = 0, aTotal = 0;
      for (String name : names) {
        bTotal += before.get(name).getUsed();
        aTotal += after.get(name).getUsed();
      }
      return new Pair<>(bTotal, aTotal);
    }

    private long getGcDuration(final Object gcInfo) throws IllegalAccessException, InvocationTargetException {
      return (Long)ourGcInfo_getGcDuration.invoke(gcInfo);
    }

    private Object getGcInfo(final CompositeData userData) throws IllegalAccessException, InvocationTargetException {
      final Object notificationInfo = ourGarbageCollectionNotificationInfo_from.invoke(null, (CompositeData)userData);
      return ourGarbageCollectionNotificationInfo_getGcInfo.invoke(notificationInfo);
    }

    @Override
    public void handleNotification(final Notification notification, final Object handback) {
      if (!isGcEventListenerInitialized) return;
      try {
        if ("com.sun.management.gc.notification".equals(notification.getType())) {
          CompositeData cd = (CompositeData)notification.getUserData();
          if (!cd.get("gcAction").toString().toLowerCase().contains("major")) {
            return;
          }
          final Object gcInfo = getGcInfo(cd);
          final long duration = getGcDuration(gcInfo);
          final Pair<Long, Long> gcMemoryDiff = getGcMemoryDiff(gcInfo);
          final long now = System.currentTimeMillis();
          FileUtil.writeFile(myGcDumpFile, now + " ; " + duration + " ; " + gcMemoryDiff.getFirst() + " ; " + gcMemoryDiff.getSecond() + "\n", "UTF-8");
        }
      } catch (Throwable t) {
        System.err.println("Exception while persisting gc notification " + notification + " to " + myGcDumpFile.getAbsolutePath() + "\n" + t);
      }
    }

    void startListen() {
      if (!isGcEventListenerInitialized) {
        return;
      }
      final List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
      for (GarbageCollectorMXBean gcBean : garbageCollectorMXBeans) {
        if (gcBean instanceof NotificationEmitter) {
          ((NotificationEmitter)gcBean).addNotificationListener(this, null, null);
        }
      }
    }
  }
}
