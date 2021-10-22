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

import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public interface TransportFactory {

  /**
   * Get a transport connection for specified local repository, URL and authentication settings
   *
   * @param r local repository to open
   * @param url URL to open
   * @param authSettings authentication settings
   * @return see above
   * @throws NotSupportedException if transport is not supported
   * @throws VcsException if there is a problem with configuring the transport
   */
  Transport createTransport(@NotNull Repository r, @NotNull final URIish url, @NotNull AuthSettings authSettings)
    throws NotSupportedException, VcsException, TransportException;

  Transport createTransport(@NotNull Repository r, @NotNull URIish url, @NotNull AuthSettings authSettings, int timeoutSeconds)
    throws NotSupportedException, VcsException, TransportException;
}
