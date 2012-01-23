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
import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.Session;
import org.jetbrains.annotations.Nullable;

/**
 * Some common functionality for SSH
 */
public class SshUtils {
  /**
   * Default SSH port
   */
  static final int SSH_PORT = 22;

  /**
   * Create SSH session
   *
   * @param sch  SSH instance to use
   * @param user the user
   * @param host the host
   * @param port the port to use
   * @return the configured session
   * @throws JSchException if session could not be created
   */
  static Session createSession(JSch sch, @Nullable Proxy proxy, String user, String host, int port) throws JSchException {
    if (port <= 0) {
      port = SSH_PORT;
    }
    final Session session = sch.getSession(user, host, port);
    session.setConfig("StrictHostKeyChecking", "no");
    if (proxy != null)
      session.setProxy(proxy);
    return session;
  }
}
