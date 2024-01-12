

package jetbrains.buildServer.buildTriggers.vcs.git.command.errors;

import java.util.regex.Pattern;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class Errors {

  private static final Pattern OUTDATED_INDEX_PATTERN = Pattern.compile(".*Entry '.+' not uptodate\\. Cannot merge\\..*", Pattern.DOTALL);

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

}