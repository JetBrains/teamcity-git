/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.Ref;
import jetbrains.buildServer.CommandLineExecutor;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.ProcessTimeoutException;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.SimpleCommandLineProcessRunner.getThreadNameCommandLine;
import static jetbrains.buildServer.util.NamedThreadFactory.executeWithNewThreadName;

/**
 * @author Mikhail Khorkov
 * @author vbedrosova
 */
public class GitProcessExecutor {

  @NotNull private final CommandLineExecutor myCommandLineExecutor;
  @NotNull private final String myCommandLine;

  @NotNull private final ExecResult myEmptyResult;
  private volatile boolean myInterrupted = false;

  @NotNull private final CommandLineExecutor.StreamGobblerFactory myOutputGobblerFactory;

  public GitProcessExecutor(@NotNull final GeneralCommandLine commandLine,
                            @NotNull final CommandLineExecutor.StreamGobblerFactory outputGobblerFactory) {
    myCommandLineExecutor = new CommandLineExecutor(commandLine);
    myCommandLine = commandLine.getCommandLineString();

    myEmptyResult = new ExecResult(SimpleCommandLineProcessRunner.getCharset(commandLine));

    myOutputGobblerFactory = outputGobblerFactory;
  }

  @NotNull
  public GitExecResult runProcess(@NotNull final byte[] input, final int idleTimeout, @NotNull ProcessExecutorListener listener) {
    final Ref<ExecResult> result = new Ref<>(myEmptyResult);
    final long startTime = System.currentTimeMillis();

    executeWithNewThreadName("Running child process: " + getThreadNameCommandLine(myCommandLine), () -> {
      myCommandLineExecutor.setIdleTimeout(idleTimeout);
      myCommandLineExecutor.setInitialProccesInput(input);
      myCommandLineExecutor.setOutpurGobblerFactory(myOutputGobblerFactory);

      try {
        listener.processStarted();
        result.set(myCommandLineExecutor.runProcess());
        listener.processFinished();
      } catch (ExecutionException e) {
        result.get().setException(e);
        listener.processFailed(e);
      }
    });

    return new GitExecResult(result.get(), System.currentTimeMillis() - startTime, myInterrupted);
  }

  public void interrupt() {
    myInterrupted = true;
    myCommandLineExecutor.destroyProcess();
  }

  public interface ProcessExecutorListener {
    void processStarted();
    void processFinished();
    void processFailed(@NotNull ExecutionException e);
  }

  public static class ProcessExecutorAdapter implements ProcessExecutorListener {
    @Override
    public void processStarted() {}
    @Override
    public void processFinished() {}
    @Override
    public void processFailed(@NotNull final ExecutionException e) {}
  }

  public static final class GitExecResult {
    @NotNull private final ExecResult myExecResult;
    private final long myDuration;
    private final boolean myInterrupted;

    public GitExecResult(@NotNull final ExecResult execResult, final long duration, final boolean interrupted) {
      myExecResult = execResult;
      myDuration = duration;
      myInterrupted = interrupted;
    }

    @NotNull
    public ExecResult getExecResult() {
      return myExecResult;
    }

    public long getDuration() {
      return myDuration;
    }

    public boolean isInterrupted() {
      return myInterrupted;
    }

    public boolean isOutOfMemoryError() {
      return myExecResult.getStderr().contains("java.lang.OutOfMemoryError");
    }

    public boolean isTimeout() {
      return myExecResult.getException() instanceof ProcessTimeoutException;
    }
  }
}