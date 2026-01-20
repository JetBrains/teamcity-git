

package jetbrains.buildServer.buildTriggers.vcs.git.command.errors;

import java.util.regex.Pattern;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Errors {

  private static final Pattern OUTDATED_INDEX_PATTERN = Pattern.compile(".*Entry '.+' not uptodate\\. Cannot merge\\..*", Pattern.DOTALL);
  private static final Pattern FATAL_MESSAGE_PATTERN = Pattern.compile("fatal: (.*)", Pattern.DOTALL);
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