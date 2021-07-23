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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.jcraft.jsch.*;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.util.jsch.JSchConfigInitializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.ssh.GitSSHHandler;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class JSchClient {

  private final static int BUF_SIZE = 32 * 1024;

  private final String myHost;
  private final String myUsername;
  private final Integer myPort;
  private final String myCommand;
  private final Logger myLogger;
  private final Map<String, String> myOptions;

  private JSchClient(@NotNull String host,
                     @Nullable String username,
                     @Nullable Integer port,
                     @NotNull String command,
                     @NotNull Logger logger,
                     @NotNull Map<String, String> options) {
    myHost = host;
    myUsername = username;
    myPort = port;
    myCommand = command;
    myLogger = logger;
    myOptions = options;
  }


  public static void main(String... args) {
    boolean debug = Boolean.parseBoolean(System.getenv(GitSSHHandler.TEAMCITY_DEBUG_SSH));
    Logger logger = debug ? new StdErrLogger() : new InMemoryLogger(Logger.INFO);
    try {
      JSchClient ssh = createClient(logger, args);
      ssh.run();
    } catch (Throwable t) {
      if (logger instanceof InMemoryLogger) {
        ((InMemoryLogger)logger).printLog();
      }
      System.err.println(t.getMessage());
      if (t instanceof NullPointerException || debug)
        t.printStackTrace();
      System.exit(1);
    }
  }


  private static JSchClient createClient(@NotNull Logger logger, String[] args) {
    return SSHCommandLine.parse(args, logger).createClient();
  }

  public void run() throws Exception {
    myLogger.log(Logger.INFO, "SSH command to run: " + myCommand);
    ChannelExec channel = null;
    Session session = null;
    try {
      JSchConfigInitializer.initJSchConfig(JSch.class);
      JSch.setLogger(myLogger);
      JSch jsch = new JSch();
      String privateKeyPath = System.getenv(GitSSHHandler.TEAMCITY_PRIVATE_KEY_PATH);
      if (privateKeyPath != null) {
        jsch.addIdentity(privateKeyPath, System.getenv(GitSSHHandler.TEAMCITY_PASSPHRASE));
      } else {
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
          File homeDir = new File(userHome);
          File ssh = new File(homeDir, ".ssh");
          File rsa = new File(ssh, "id_rsa");
          if (rsa.isFile()) {
            jsch.addIdentity(rsa.getAbsolutePath());
          }
          File dsa = new File(ssh, "id_dsa");
          if (dsa.isFile()) {
            jsch.addIdentity(dsa.getAbsolutePath());
          }
          File config = new File(ssh, "config");
          if (config.isFile()) {
            ConfigRepository configRepository = OpenSSHConfig.parseFile(config.getAbsolutePath());
            jsch.setConfigRepository(new TeamCityConfigRepository(configRepository, myUsername));
          }
        }
      }
      session = jsch.getSession(myUsername, myHost, myPort != null ? myPort : 22);

      String teamCityVersion = System.getenv(GitSSHHandler.TEAMCITY_VERSION);
      if (teamCityVersion != null) {
        session.setClientVersion(GitUtils.getSshClientVersion(session.getClientVersion(), teamCityVersion));
      }

      if (Boolean.parseBoolean(System.getenv(GitSSHHandler.SSH_IGNORE_KNOWN_HOSTS_ENV))) {
        session.setConfig("StrictHostKeyChecking", "no");
      } else {
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
          File homeDir = new File(userHome);
          File ssh = new File(homeDir, ".ssh");
          File knownHosts = new File(ssh, "known_hosts");
          if (knownHosts.isFile()) {
            try {
              jsch.setKnownHosts(knownHosts.getAbsolutePath());
            } catch (Exception e) {
              myLogger.log(Logger.WARN, "Failed to configure known hosts: '" + e.toString() + "'");
            }
          }
        }
      }

      String authMethods = System.getenv(GitSSHHandler.TEAMCITY_SSH_PREFERRED_AUTH_METHODS);
      if (isNotEmpty(authMethods))
        session.setConfig("PreferredAuthentications", authMethods);

      if (!myOptions.isEmpty()) {
        for (final Map.Entry<String, String> opEntry : myOptions.entrySet()) {
          session.setConfig(opEntry.getKey(), opEntry.getValue());
        }
      }

      EmptySecurityCallbackHandler.install();

      // It looks like sometimes session/channel close() doesn't interrupt
      // all reads. Ask jsch to create daemon threads so that uninterrupted
      // threads don't prevent us from exit.
      session.setDaemonThread(true);

      session.connect();

      channel = (ChannelExec) session.openChannel("exec");
      channel.setPty(false);
      channel.setCommand(myCommand);
      channel.setInputStream(System.in);
      channel.setErrStream(System.err);

      final String sendEnv = System.getenv(GitSSHHandler.TEAMCITY_SSH_REQUEST_TOKEN);
      if (isNotEmpty(sendEnv)) {
        channel.setEnv(GitSSHHandler.TEAMCITY_SSH_REQUEST_TOKEN, sendEnv);
      }

      InputStream input = channel.getInputStream();
      Integer timeoutSeconds = getTimeoutSeconds();
      if (timeoutSeconds != null) {
        channel.connect(timeoutSeconds * 1000);
      } else {
        channel.connect();
      }


      if (!channel.isConnected() && input.available() == 0) {
        throw new IOException("Connection failed");
      }

      Copy copyThread = new Copy(input);
      if (timeoutSeconds != null) {
        new Timer(copyThread, timeoutSeconds).start();
      }
      copyThread.start();
      copyThread.join();
      copyThread.rethrowError();
    } finally {
      if (channel != null)
        channel.disconnect();
      if (session != null)
        session.disconnect();
    }
  }

  public static boolean isNotEmpty(@Nullable String s) {
    return s != null && s.length() > 0;
  }

  @Nullable
  private Integer getTimeoutSeconds() {
    String timeout = System.getenv(GitSSHHandler.TEAMCITY_SSH_IDLE_TIMEOUT_SECONDS);
    if (timeout == null)
      return null;
    try {
      return Integer.parseInt(timeout);
    } catch (NumberFormatException e) {
      myLogger.log(Logger.WARN, "Failed to parse idle timeout: '" + timeout + "'");
      return null;
    }
  }


  private class Timer extends Thread {
    private final long myThresholdNanos;
    private volatile Copy myCopyThread;
    Timer(@NotNull Copy copyThread, long timeoutSeconds) {
      myCopyThread = copyThread;
      myThresholdNanos = TimeUnit.SECONDS.toNanos(timeoutSeconds);
      setDaemon(true);
      setName("Timer");
    }

    @Override
    public void run() {
      boolean logged = false;
      long sleepInterval = Math.min(TimeUnit.SECONDS.toMillis(10), TimeUnit.NANOSECONDS.toMillis(myThresholdNanos));
      //noinspection InfiniteLoopStatement: it is a daemon thread and doesn't prevent process from termination
      while (true) {
        if (System.nanoTime() - myCopyThread.getTimestamp() > myThresholdNanos) {
          if (!logged) {
            myLogger.log(Logger.ERROR, String.format("Timeout error: no activity for %s seconds", TimeUnit.NANOSECONDS.toSeconds(myThresholdNanos)));
            logged = true;
          }
          myCopyThread.interrupt();
        } else {
          try {
            Thread.sleep(sleepInterval);
          } catch (Exception e) {
            //ignore
          }
        }
      }
    }
  }


  private class Copy extends Thread {
    private final InputStream myInput;
    private final AtomicLong myTimestamp = new AtomicLong(System.nanoTime());
    private volatile Exception myError;
    Copy(@NotNull InputStream input) {
      myInput = input;
      setName("Copy");
    }

    @Override
    public void run() {
      byte[] buffer = new byte[BUF_SIZE];
      int count;
      try {
        while ((count = myInput.read(buffer)) != -1) {
          System.out.write(buffer, 0, count);
          myTimestamp.set(System.nanoTime());
          if (System.out.checkError()) {
            myLogger.log(Logger.ERROR, "Error while writing to stdout");
            throw new IOException("Error while writing to stdout");
          }
        }
      } catch (Exception e) {
        myError = e;
      }
    }

    long getTimestamp() {
      return myTimestamp.get();
    }

    void rethrowError() throws Exception {
      if (myError != null)
        throw myError;
    }
  }


  private static class StdErrLogger implements Logger {
    private final SimpleDateFormat myDateFormat = new SimpleDateFormat("[HH:mm:ss.SSS]");
    @Override
    public boolean isEnabled(final int level) {
      return true;
    }

    @Override
    public void log(final int level, final String message) {
      System.err.print(getTimestamp());
      System.err.print(" ");
      System.err.print(getLevel(level));
      System.err.print(" ");
      System.err.println(message);
    }

    @NotNull
    private String getTimestamp() {
      synchronized (myDateFormat) {
        return myDateFormat.format(new Date());
      }
    }
  }


  private static class InMemoryLogger implements Logger {
    private final int myMinLogLevel;
    private final List<LogEntry> myLogEntries;
    InMemoryLogger(int minLogLevel) {
      myMinLogLevel = minLogLevel;
      myLogEntries = new ArrayList<LogEntry>();
    }

    @Override
    public boolean isEnabled(final int level) {
      return level >= myMinLogLevel;
    }

    @Override
    public void log(final int level, final String message) {
      if (isEnabled(level)) {
        synchronized (myLogEntries) {
          myLogEntries.add(new LogEntry(System.currentTimeMillis(), level, message));
        }
      }
    }

    void printLog() {
      SimpleDateFormat dateFormat = new SimpleDateFormat("[HH:mm:ss.SSS]");
      synchronized (myLogEntries) {
        for (LogEntry entry : myLogEntries) {
          System.err.print(dateFormat.format(new Date(entry.myTimestamp)));
          System.err.print(" ");
          System.err.print(getLevel(entry.myLogLevel));
          System.err.print(" ");
          System.err.println(entry.myMessage);
        }
      }
    }

    private static class LogEntry {
      private final long myTimestamp;
      private final int myLogLevel;
      private final String myMessage;
      LogEntry(long timestamp, int logLevel, @NotNull String message) {
        myTimestamp = timestamp;
        myLogLevel = logLevel;
        myMessage = message;
      }
    }
  }


  @NotNull
  private static String getLevel(int level) {
    switch (level) {
      case Logger.DEBUG:
        return "DEBUG";
      case Logger.INFO:
        return "INFO";
      case Logger.WARN:
        return "WARN";
      case Logger.ERROR:
        return "ERROR";
      case Logger.FATAL:
        return "FATAL";
      default:
        return "UNKNOWN";
    }
  }


  // Doesn't provide any credentials, used instead the default handler from jdk
  // which reads credentials them from stdin.
  public static class EmptySecurityCallbackHandler implements CallbackHandler {
    @Override
    public void handle(final Callback[] callbacks) throws UnsupportedCallbackException {
      if (callbacks.length > 0) {
        throw new UnsupportedCallbackException(callbacks[0], "Unsupported callback");
      }
    }

    static void install() {
      Security.setProperty("auth.login.defaultCallbackHandler", EmptySecurityCallbackHandler.class.getName());
    }
  }


  // Need to wrap jsch config to workaround its bugs:
  // https://bugs.eclipse.org/bugs/show_bug.cgi?id=526778
  // https://bugs.eclipse.org/bugs/show_bug.cgi?id=526867
  private static class TeamCityConfigRepository implements ConfigRepository {
    private final ConfigRepository myDelegate;
    private final String myUser;
    TeamCityConfigRepository(@NotNull ConfigRepository delegate, @Nullable String user) {
      myDelegate = delegate;
      myUser = user;
    }

    @Override
    public Config getConfig(final String host) {
      Config config = myDelegate.getConfig(host);
      return config != null ? new TeamCityConfig(config, myUser) : null;
    }
  }

  private static class TeamCityConfig implements ConfigRepository.Config {
    private final ConfigRepository.Config myDelegate;
    private final String myUser;
    TeamCityConfig(@NotNull ConfigRepository.Config delegate, @Nullable String user) {
      myDelegate = delegate;
      myUser = user;
    }

    @Override
    public String getHostname() {
      return myDelegate.getHostname();
    }

    @Override
    public String getUser() {
      // https://bugs.eclipse.org/bugs/show_bug.cgi?id=526778
      // enforce our username
      return myUser != null ? myUser : myDelegate.getUser();
    }

    @Override
    public int getPort() {
      return myDelegate.getPort();
    }

    @Override
    public String getValue(final String key) {
      String result = myDelegate.getValue(key);
      if (result != null) {
        if ("ServerAliveInterval".equalsIgnoreCase(key) || "ConnectTimeout".equalsIgnoreCase(key)) {
          // https://bugs.eclipse.org/bugs/show_bug.cgi?id=526867
          // these timeouts are in seconds, jsch treats them as milliseconds which causes timeout errors
          try {
            result = Long.toString(TimeUnit.SECONDS.toMillis(Integer.parseInt(result)));
          } catch (NumberFormatException e) {
            // Ignore
          }
        }
      }
      return result;
    }

    @Override
    public String[] getValues(final String key) {
      return myDelegate.getValues(key);
    }
  }

  public static class SSHCommandLine {

    private String myHost;
    private String myUser;
    private Integer myPort;
    private String myCommand;
    private Map<String, String> options;
    private final Logger myLogger;

    private SSHCommandLine(
      @NotNull final String host,
      @Nullable final String user,
      @Nullable final Integer port,
      @NotNull final String command,
      @NotNull final Map<String, String> options,
      @NotNull final Logger logger) {
      myHost = host;
      myUser = user;
      myPort = port;
      myCommand = command;
      this.options = options;
      myLogger = logger;
    }

    @NotNull
    public String getHost() {
      return myHost;
    }

    @Nullable
    public String getUser() {
      return myUser;
    }

    @Nullable
    public Integer getPort() {
      return myPort;
    }

    @NotNull
    public String getCommand() {
      return myCommand;
    }

    @NotNull
    public Map<String, String> getOptions() {
      return options;
    }

    @NotNull
    public Logger getLogger() {
      return myLogger;
    }

    /**
     * Git runs ssh as follows (<a href="https://git-scm.com/book/en/v2/Git-Internals-Environment-Variables">Docs</a>):
     *
     * <code>$GIT_SSH [-p &lt;port&gt;] [username@]host &lt;command&gt;</code>
     * <p>
     * e.g. <code>$GIT_SSH 'git@server.com' 'git-upload-pack '\''user/repo.git'\'''</code>
     * <p>
     * The git-upload-pack command and its args are passed as a single argument to $GIT_SSH.
     * <p>
     * Git LFS also uses $GIT_SSH, but it doesn't combine all arguments into a single $GIT_SSH arg:
     * <p>
     * <code>$GIT_SSH 'git@server.com' 'git-lfs-authenticate' 'user/repo.git' 'download'</code>
     * <p>
     * we need to combine them ourselves.
     */
    public static SSHCommandLine parse(String[] args, Logger logger) {
      final String line = join(args);
      logger.log(Logger.DEBUG, "Call ssh: " + line);

      LinkedList<String> list = new LinkedList<String>(Arrays.asList(args));

      Integer port = null;
      final Map<String, String> options = new HashMap<String, String>();
      for (Iterator<String> it = list.iterator(); it.hasNext(); ) {
        final String next = it.next();
        if ("-o".equals(next)) {
          it.remove();
          final String[] op = it.next().split("=");
          options.put(op[0], op[1]);
          it.remove();

        } else if ("-p".equals(next)) {
          it.remove();
          port = Integer.parseInt(it.next());
          it.remove();
        }
      }

      String user;
      String host = list.pollFirst();
      int atIndex = host.lastIndexOf('@');
      if (atIndex == -1) {
        user = null;
      } else {
        user = host.substring(0, atIndex);
        host = host.substring(atIndex + 1);
      }

      final String command = join(list);

      return new SSHCommandLine(host, user, port, command, options, logger);
    }

    private static String join(String[] toJoin) {
      return join(Arrays.asList(toJoin));
    }

    private static String join(Iterable toJoin) {
      final String separator = " ";
      final StringBuilder result = new StringBuilder();
      final Iterator it = toJoin.iterator();
      while (it.hasNext()) {
        Object item = it.next();
        if (item != null) {
          result.append(item);
          if (it.hasNext()) {
            result.append(separator);
          }
        }
      }
      return result.toString();
    }

    public JSchClient createClient() {
      return new JSchClient(myHost, myUser, myPort, myCommand, myLogger, options);
    }
  }
}
