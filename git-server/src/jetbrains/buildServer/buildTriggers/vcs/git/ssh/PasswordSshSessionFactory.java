/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.ssh;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A simple session factory used for password authentication.
 * It assume that password was passed in URL to ssh.
 */
public class PasswordSshSessionFactory extends SshSessionFactory {
  //An ssh instance with no keys registered
  private final JSch mySch;
  private final ServerPluginConfig myConfig;
  private final Map<String, String> myJschOptions;
  
  public PasswordSshSessionFactory(@NotNull final ServerPluginConfig config, @NotNull Map<String, String> jschOptions) {
    mySch = new JSch();
    myConfig = config;
    myJschOptions = jschOptions;
  }

  @Override
  public Session getSession(String user, String pass, String host, int port, CredentialsProvider credentialsProvider, FS fs)
    throws JSchException {
    final Session session = SshUtils.createSession(mySch, myConfig.getJschProxy(), user, host, port);
    session.setPassword(pass);
    if (!myConfig.alwaysCheckCiphers()) {
      for (Map.Entry<String, String> entry : myJschOptions.entrySet()) {
        session.setConfig(entry.getKey(), entry.getValue());
      }
    }
    return session;
  }
}
