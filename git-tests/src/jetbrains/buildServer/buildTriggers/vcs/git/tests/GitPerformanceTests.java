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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.OperationContext;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.command.NativeGitCommands;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.StubContext;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.RepositoryStateData;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import jetbrains.buildServer.vcs.patches.PatchBuilderImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.Test;

/**
 * class for local experiments on performance.
 * This test is not intended to be executed in CI
 *
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 */
public class GitPerformanceTests extends BaseTestCase {
  @Test(invocationCount = 100)
  public void incrementalIntellijFetch() throws Exception {
    final VcsRootImpl root = VcsRootBuilder.vcsRoot()
                                           .withBranchSpec("+:refs/heads/*")
                                           .withFetchUrl("ssh://git@git.jetbrains.team/intellij.git")
                                           .withAuthMethod(AuthenticationMethod.PRIVATE_KEY_DEFAULT).build();

    final ServerPaths sp = new ServerPaths("/Users/victory/Tests/server_paths");

    final ServerPluginConfig config = new PluginConfigBuilder(sp).setSeparateProcessForFetch(false).build();
    final GitSupportBuilder builder = GitSupportBuilder
      .gitSupport()
      .withServerPaths(sp)
      .withPluginConfig(config)
      .withFetchCommand(new NativeGitCommands(config, () -> new StubContext().getGitExec(), new VcsRootSshKeyManager() {
        @Nullable
        @Override
        public TeamCitySshKey getKey(@NotNull VcsRoot root) {
          return null;
        }
      }));
    GitVcsSupport support = builder.build();

    final RepositoryStateData currentState = support.getCurrentState(root);

    final long startTime = new Date().getTime();
    System.out.println("Fetching repository...");

    final OperationContext ctx = support.createContext(root, "fetch");
    try {
      support.getCollectChangesPolicy().ensureRepositoryStateLoadedFor(ctx, currentState, true);
    } finally {
      ctx.close();
    }

    final long totalTime = new Date().getTime() - startTime;
    System.out.println("Fetch time: " + totalTime + "ms");
  }

  @Test
  public void toCleanCheckoutTest() throws Exception {

    final VcsRootImpl root = VcsRootBuilder.vcsRoot().withFetchUrl("E:\\Work\\idea-ultimate").build();
    final ServerPaths sp = new ServerPaths(createTempDir().getPath());
    final GitSupportBuilder builder = GitSupportBuilder
      .gitSupport()
      .withServerPaths(sp)
      .withPluginConfig(new PluginConfigBuilder(sp).build());
    GitVcsSupport support = builder.build();

    RepositoryStateData s = support.getCurrentState(root);
    final String state = s.getBranchRevisions().get(s.getDefaultBranchName());
    System.out.println("Current state: " + state);


    final long startTime = new Date().getTime();
    System.out.println("Fetching repository...");
    //make sure repository is cloned
    final OperationContext ctx = support.createContext(root, "fetch");
    try {
      builder.getCommitLoader().loadCommit(ctx, ctx.getGitRoot(), state);
    } finally {
      ctx.close();
    }
    final long totalTime = new Date().getTime() - startTime;
    System.out.println("Clone time: " + totalTime + "ms");


    runCleanCheckout(root, support, state);

    //some older state
    runCleanCheckout(root, support, "395c1639ee346816048b1b74cec83ab4dd162451");
  }

  private void runCleanCheckout(@NotNull final VcsRootImpl root,
                                @NotNull final GitVcsSupport support,
                                @NotNull final String state) {
    System.out.println("Running clean checkout... " + state);
    BaseTestCase.assertTime(42* 20, "clean checkout", 3, new Runnable() {
      public void run() {
        final AtomicLong sz = new AtomicLong(0);
        try {
          support.buildPatch(root, null, state, mockBuilder(sz), CheckoutRules.createOn(".=>."));
        } catch (Exception e) {
          throw new RuntimeException("failed", e);
        }
        System.out.println("Clean patch complete: size: " + sz.get());
      }
    });
  }

  @NotNull
  private PatchBuilder mockBuilder(@NotNull final AtomicLong size) {
    return new PatchBuilderImpl(new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        size.incrementAndGet();
      }
    });
  }

}
