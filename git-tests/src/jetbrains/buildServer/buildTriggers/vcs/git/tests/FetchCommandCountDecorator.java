/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.FetchSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

class FetchCommandCountDecorator implements FetchCommand {

  private final FetchCommand myDelegate;
  private int myFetchCount = 0;

  FetchCommandCountDecorator(FetchCommand delegate) {
    myDelegate = delegate;
  }

  public void fetch(@NotNull Repository db,
                    @NotNull URIish fetchURI,
                    @NotNull Collection<RefSpec> refspecs,
                    @NotNull FetchSettings settings) throws NotSupportedException,
                                                            VcsException,
                                                            TransportException {
    myDelegate.fetch(db, fetchURI, refspecs, settings);
    inc();
  }

  private synchronized void inc() {
    myFetchCount++;
  }

  public synchronized int getFetchCount() {
    return myFetchCount;
  }

  public synchronized void resetFetchCounter() {
    myFetchCount = 0;
  }
}
