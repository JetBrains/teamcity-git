

package jetbrains.buildServer.buildTriggers.vcs.git.command.errors;

import java.util.regex.Pattern;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Errors {

  private static final Pattern OUTDATED_INDEX_PATTERN = Pattern.compile(".*Entry '.+' not uptodate\\. Cannot merge\\..*", Pattern.DOTALL);
  // no DOTALL here: the group must capture the "fatal:" line only. With DOTALL it greedily captures everything
  // after the first "fatal:" (the rest of the git stderr and the whole ssh trace), and BaseAuthCommandImpl
  // then repeats all of it after "Full error: ".
  private static final Pattern FATAL_MESSAGE_PATTERN = Pattern.compile("fatal: (.*)");
  private static final Pattern REMOTE_MESSAGE_PATTERN = Pattern.compile("remote: (.*)");

  public static boolean isCorruptedIndexError(@NotNull VcsException e) {
    String msg = e.getMessage();
    if (msg == null)
      return false;
    return msg.contains("fatal: index file smaller than expected") || msg.contains("fatal: index file corrupt");
  }


  public static boolean isOutdatedIndexError(@NotNull VcsException e) {
    String msg = e.getMessage();
    if (msg == null)
      return false;
    return OUTDATED_INDEX_PATTERN.matcher(msg).matches();
  }

  public static boolean isAuthenticationFailedError(@NotNull VcsException e) {
    String msg = e.getMessage();
    if (msg == null)
      return false;
    return msg.toLowerCase().contains("authentication failed");
  }
  
  @Nullable
  public static String getFatalMessage(@NotNull VcsException e) {
    String msg = e.getMessage();
    if (msg == null) {
      return null;
    }
    return StringUtil.substringByRegex(msg, FATAL_MESSAGE_PATTERN, 1);
  }

  @Nullable
  public static String getRemoteMessage(@NotNull VcsException e) {
    String msg = e.getMessage();
    if (msg == null) {
      return null;
    }
    return StringUtil.substringByRegex(msg, REMOTE_MESSAGE_PATTERN, 1);
  }

}