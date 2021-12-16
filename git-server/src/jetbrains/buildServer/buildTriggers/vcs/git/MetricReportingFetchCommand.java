/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import java.io.IOException;
import jetbrains.buildServer.metrics.Counter;
import jetbrains.buildServer.metrics.Stoppable;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

public class MetricReportingFetchCommand implements FetchCommand {
  private final FetchCommand myDelegate;
  private final Counter myFetchDurationTimer;

  public MetricReportingFetchCommand(@NotNull final FetchCommand delegate, @NotNull Counter fetchDurationTimer) {
    myDelegate = delegate;
    myFetchDurationTimer = fetchDurationTimer;
  }

  @Override
  public void fetch(@NotNull final Repository db,
                    @NotNull final URIish fetchURI,
                    @NotNull final FetchSettings settings) throws IOException, VcsException {
    try (Stoppable ignored = myFetchDurationTimer.startMsecsTimer()) {
      myDelegate.fetch(db, fetchURI, settings);
    }
  }
}
