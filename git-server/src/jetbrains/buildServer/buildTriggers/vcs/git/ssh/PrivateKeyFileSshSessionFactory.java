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

package jetbrains.buildServer.buildTriggers.vcs.git.ssh;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.Settings;
import jetbrains.buildServer.buildTriggers.vcs.git.VcsAuthenticationException;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The SSH session factory that uses explicitly specified private key file
 * for authentication.
 */
public class PrivateKeyFileSshSessionFactory extends SshSessionFactory {
  //SSH instance with registered identity
  private final JSch mySch;
  private final ServerPluginConfig myConfig;
  private final Map<String, String> myJschOptions;

  public PrivateKeyFileSshSessionFactory(@NotNull ServerPluginConfig config,
                                         @NotNull Settings.AuthSettings settings,
                                         @NotNull Map<String, String> jschOptions) throws VcsException {
    myConfig = config;
    myJschOptions = jschOptions;
    final String privateKeyPath = settings.getPrivateKeyFilePath();
    final String passphrase = settings.getPassphrase();
    if(privateKeyPath == null || privateKeyPath.length() == 0) {
      throw new VcsAuthenticationException("The private key path is not specified");
    }
    this.mySch = new JSch();
    try {
      mySch.addIdentity(privateKeyPath, passphrase);
    } catch (JSchException e) {
      throw new VcsAuthenticationException("Unable to load identity file: " + privateKeyPath + (passphrase != null ? " (passphrase protected)" : ""), e);
    }
  }

  @Override
  public Session getSession(String user, String pass, String host, int port, CredentialsProvider credentialsProvider, FS fs)
    throws JSchException {
    final Session session = SshUtils.createSession(mySch, myConfig.getJschProxy(), user, host, port);
    if (!myConfig.alwaysCheckCiphers()) {
      for (Map.Entry<String, String> entry : myJschOptions.entrySet()) {
        session.setConfig(entry.getKey(), entry.getValue());
      }
    }
    return session;
  }
}
