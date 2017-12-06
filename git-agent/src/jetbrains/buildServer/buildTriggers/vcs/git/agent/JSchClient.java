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
import com.jcraft.jsch.Session;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.ssh.GitSSHHandler;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;

public class JSchClient {

  private final static int BUF_SIZE = 32 * 1024;

  private final String myHost;
  private final String myUsername;
  private final Integer myPort;
  private final String myCommand;


  private JSchClient(@NotNull String host, @Nullable String username, @Nullable Integer port, @NotNull String command) {
    myHost = host;
    myUsername = username;
    myPort = port;
    myCommand = command;
  }


  public static void main(String... args) {
    boolean debug = Boolean.parseBoolean(System.getenv(GitSSHHandler.TEAMCITY_DEBUG_SSH));
    try {
      JSchClient ssh = createClient(args);
      ssh.run();
    } catch (Throwable t) {
      System.err.println(t.getMessage());
      if (t instanceof NullPointerException || debug)
        t.printStackTrace();
      System.exit(1);
    }
  }


  private static JSchClient createClient(String[] args) {
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
    return new JSchClient(host, user, port, command);
  }


  public void run() throws Exception {
    ChannelExec channel = null;
    Session session = null;
    try {
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
      }

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
}
