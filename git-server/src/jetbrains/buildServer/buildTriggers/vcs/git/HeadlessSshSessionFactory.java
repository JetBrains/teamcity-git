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

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.jcraft.jsch.Session;
import org.spearce.jgit.transport.OpenSshConfig;
import org.spearce.jgit.transport.SshConfigSessionFactory;

/**
 * A headless SSH session factory that is based on ~/.ssh/config settings.
 * It is used in case when the default host specific configuration
 * should be used.
 */
public class HeadlessSshSessionFactory extends SshConfigSessionFactory {
  /**
   * {@inheritDoc}
   */
  protected void configure(OpenSshConfig.Host hc, Session session) {
    // do nothing, UserInfo will not be set and openning connection will fail
  }
}
