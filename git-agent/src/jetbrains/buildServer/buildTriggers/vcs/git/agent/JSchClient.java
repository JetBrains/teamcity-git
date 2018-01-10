/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Logger;
import com.jcraft.jsch.Session;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.ssh.GitSSHHandler;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.File;
import java.io.InputStream;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class JSchClient {

  private final static int BUF_SIZE = 32 * 1024;

  private final String myHost;
  private final String myUsername;
  private final Integer myPort;
  private final String myCommand;
  private final Logger myLogger;

  private JSchClient(@NotNull String host,
                     @Nullable String username,
                     @Nullable Integer port,
                     @NotNull String command,
                     @NotNull Logger logger) {
    myHost = host;
    myUsername = username;
    myPort = port;
    myCommand = command;
    myLogger = logger;
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
    if (args.length != 2 && args.length != 4) {
      System.err.println("Invalid arguments " + Arrays.asList(args));
      System.exit(1);
    }

    int i = 0;
    Integer port = null;
    //noinspection HardCodedStringLiteral
    if ("-p".equals(args[i])) {
      i++;
      port = Integer.parseInt(args[i++]);
    }
    String host = args[i++];
    String user;
    int atIndex = host.lastIndexOf('@');
    if (atIndex == -1) {
      user = null;
    }
    else {
      user = host.substring(0, atIndex);
      host = host.substring(atIndex + 1);
    }
    String command = args[i];
    return new JSchClient(host, user, port, command, logger);
  }


  public void run() throws Exception {
    ChannelExec channel = null;
    Session session = null;
    try {
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
      if (authMethods != null && authMethods.length() > 0)
        session.setConfig("PreferredAuthentications", authMethods);

      EmptySecurityCallbackHandler.install();

      session.connect();

      channel = (ChannelExec) session.openChannel("exec");
      channel.setPty(false);
      channel.setCommand(myCommand);
      channel.setInputStream(System.in);
      channel.setErrStream(System.err);
      channel.connect();

      InputStream input = channel.getInputStream();
      byte[] buffer = new byte[BUF_SIZE];
      int count;
      while ((count = input.read(buffer)) != -1) {
        System.out.write(buffer, 0, count);
      }
    } finally {
      if (channel != null)
        channel.disconnect();
      if (session != null)
        session.disconnect();
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
}
