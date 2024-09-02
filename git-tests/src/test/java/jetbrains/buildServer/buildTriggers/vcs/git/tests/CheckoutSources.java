package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentVcsSupport;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jetbrains.annotations.NotNull;

public class CheckoutSources extends Thread {
  private final VcsRootImpl myRoot;
  private final String myRevision;
  private final File myBuildDir;
  private final AgentRunningBuild myBuild;
  private final AtomicBoolean mySuccess = new AtomicBoolean(false);
  private final GitAgentVcsSupport myVcsSupport;

  CheckoutSources( @NotNull VcsRootImpl root,
                   @NotNull String revision,
                   @NotNull File buildDir,
                   @NotNull AgentRunningBuild build,
                   @NotNull GitAgentVcsSupport vcsSupport) {
    myRoot = root;
    myRevision = revision;
    myBuildDir = buildDir;
    myBuild = build;
    myVcsSupport = vcsSupport;
  }


  public void run(long timeoutMillis) throws InterruptedException {
    start();
    join(timeoutMillis);
  }


  @Override
  public void run() {
    try {
      myVcsSupport.updateSources(myRoot, CheckoutRules.DEFAULT, myRevision, myBuildDir, myBuild, false);
      mySuccess.set(true);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  boolean success() {
    return mySuccess.get();
  }
}
