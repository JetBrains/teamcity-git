/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.Settings;
import jetbrains.buildServer.vcs.VcsException;
import org.spearce.jgit.transport.SshSessionFactory;

/**
 * The SSH session factory that uses explicitly specified private key file
 * for authentication.
 */
public class PrivateKeyFileSshSessionFactory extends SshSessionFactory {
  /**
   * SSH instance with registered identity
   */
  private final JSch sch;

  /**
   * A construction
   *
   * @param privateKeyPath a path to the key
   * @param passphrase     a passphrase for the key
   * @throws VcsException if private key could not be read
   */
  public PrivateKeyFileSshSessionFactory(String privateKeyPath, String passphrase) throws VcsException {
    this.sch = new JSch();
    try {
      sch.addIdentity(privateKeyPath, passphrase);
    } catch (JSchException e) {
      throw new VcsException("Unable to load identity file: " + privateKeyPath + (passphrase != null ? " (passphrase protected)" : ""), e);
    }
  }

  /**
   * A constructor from vcs root settings
   *
   * @param s a settings object
   * @throws VcsException if private key could not be read
   */
  public PrivateKeyFileSshSessionFactory(Settings s) throws VcsException {
    this(s.getPrivateKeyFile(), s.getPassphrase());
  }

  /**
   * {@inheritDoc}
   */
  public Session getSession(String user, String pass, String host, int port) throws JSchException {
    return SshUtils.createSession(sch, user, host, port);
  }
}
