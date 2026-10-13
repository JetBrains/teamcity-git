package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.ProcessTimeoutException;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.ProcessTimeoutCallback;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ProcessTimeoutCallbackTest {
  @Test
  public void timeout_allows_sigterm_cleanup_on_unix() throws Exception {
    if (!SystemInfo.isUnix) throw new SkipException("SIGTERM is a Unix signal");

    Path marker = Files.createTempFile("git-process-cleanup", ".txt");
    AtomicReference<Process> process = new AtomicReference<>();
    try {
      ProcessTimeoutCallback callback = new ProcessTimeoutCallback(1) {
        @Override
        public void onProcessStarted(Process started) {
          process.set(started);
        }
      };
      // A runner that cannot resolve the PID can fall back to SIGTERM even with tree killing enabled.
      assertFalse(callback.terminateEntireProcessTree(), "Git must not request forced process-tree termination on Unix");
      GeneralCommandLine command = new GeneralCommandLine();
      command.setExePath("/bin/sh");
      // Use shell built-ins only, so there are no child processes left behind by the test.
      command.addParameters("-c", "trap 'printf terminated > \"$1\"; exit 0' TERM; printf running > \"$1\"; while :; do :; done",
                            "git-process-cleanup", marker.toString());
      ExecResult result = SimpleCommandLineProcessRunner.runCommand(command, new byte[0], callback);

      assertTrue(result.getException() instanceof ProcessTimeoutException, "The process should time out");
      assertTrue(process.get().waitFor(5, TimeUnit.SECONDS), "The process should exit after SIGTERM");
      assertEquals(new String(Files.readAllBytes(marker), StandardCharsets.UTF_8), "terminated",
                   "The SIGTERM handler should be able to clean up before the process exits");
    } finally {
      Process started = process.get();
      if (started != null && started.isAlive()) {
        started.destroyForcibly();
        started.waitFor(5, TimeUnit.SECONDS);
      }
      Files.deleteIfExists(marker);
    }
  }
}
