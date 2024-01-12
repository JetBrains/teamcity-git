

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TimePrinter;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
public final class CommandLineUtil {

  private CommandLineUtil() {}


  @Nullable
  public static VcsException getCommandLineError(@NotNull String cmdName, @NotNull ExecResult res) {
    return getCommandLineError(cmdName, res, true, true);
  }


  @Nullable
  public static VcsException getCommandLineError(@NotNull String cmdName, @NotNull ExecResult res, boolean includeStdOut, boolean includeStdErr) {
    return getCommandLineError(cmdName, "", res, includeStdOut, includeStdErr);
  }

  @Nullable
  public static VcsException getCommandLineError(@NotNull String cmdName, @NotNull String details, @NotNull ExecResult res, boolean includeStdOut, boolean includeStdErr) {
    //noinspection ThrowableResultOfMethodCallIgnored
    Throwable exception = res.getException();
    int exitCode = res.getExitCode();
    if (exitCode != 0 || exception != null) {
      String stderr = res.getStderr();
      String stdout = res.getStdout();
      final String message = "'" + cmdName + "' command failed" + details + "." +
                             (exception != null ? "\nexception: " + exception.toString() : "") +
                             (includeStdErr && !StringUtil.isEmpty(stderr) ? "\nstderr: " + stderr.trim() : "") +
                             (includeStdOut && !StringUtil.isEmpty(stdout) ? "\nstdout: " + stdout.trim() : "") +
                             (res.getElapsedTime() != -1 ? "\nelapsed time: " + TimePrinter.createMillisecondsFormatter().formatTime(res.getElapsedTime()) : "") +
                             (exitCode != 0 ? "\nexit code: " + exitCode : "");
      return exception == null ? new VcsException(message) : new VcsException(message, exception);
    } else {
      return null;
    }
  }
}