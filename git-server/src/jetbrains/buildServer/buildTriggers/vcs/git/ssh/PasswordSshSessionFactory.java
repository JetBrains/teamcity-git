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
import org.eclipse.jgit.transport.SshSessionFactory;

/**
 * A simple session factory used for password authentication.
 * It assume that password was passed in URL to ssh.
 */
public class PasswordSshSessionFactory extends SshSessionFactory {
  /**
   * An instance of the session factory
   */
  public static final SshSessionFactory INSTANCE = new PasswordSshSessionFactory();
  /**
   * An ssh instance with no keys registered
   */
  private final JSch sch;

  /**
   * A private constructor to ensure that only one instance is created
   */
  private PasswordSshSessionFactory() {
    sch = new JSch();
  }

  /**
   * {@inheritDoc}
   */
  public Session getSession(String user, String pass, String host, int port) throws JSchException {
    final Session session = SshUtils.createSession(sch, user, host, port);
    session.setPassword(pass);
    return session;
  }
}
