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

import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class LimitingRevWalk extends RevWalk {
  private final ServerPluginConfig myConfig;
  private final OperationContext myContext;
  private final GitVcsRoot myGitRoot;
  private final Repository myRepository;
  private int myNextCallCount = 0;
  private RevCommit myCurrentCommit;
  private int myNumberOfCommitsToVisit = -1;

  LimitingRevWalk(@NotNull ServerPluginConfig config, @NotNull OperationContext context) throws VcsException {
    super(context.getRepository());
    myConfig = config;
    myContext = context;
    myGitRoot = context.getGitRoot();
    myRepository = context.getRepository();
  }

  @Override
  public RevCommit next() throws IOException {
    myCurrentCommit = super.next();
    myNextCallCount++;
    if (myCurrentCommit != null && shouldLimitByNumberOfCommits() && myNextCallCount > myNumberOfCommitsToVisit) {
      myCurrentCommit = null;
    }
    return myCurrentCommit;
  }


  public void limitByNumberOfCommits(final int numberOfCommitsToVisit) {
    myNumberOfCommitsToVisit = numberOfCommitsToVisit;
  }

  @NotNull
  public RevCommit getCurrentCommit() {
    checkCurrentCommit();
    return myCurrentCommit;
  }

  private boolean shouldLimitByNumberOfCommits() {
    return myNumberOfCommitsToVisit != -1;
  }

  protected void checkCurrentCommit() {
    if (myCurrentCommit == null)
      throw new IllegalStateException("Current commit is null");
  }

  @NotNull
  public OperationContext getContext() {
    return myContext;
  }

  @NotNull
  public GitVcsRoot getGitRoot() {
    return myGitRoot;
  }

  @NotNull
  public Repository getRepository() {
    return myRepository;
  }

  @NotNull
  public ServerPluginConfig getConfig() {
    return myConfig;
  }

  public int getVisitedCommitsNum() {
    return myNextCallCount;
  }
}
