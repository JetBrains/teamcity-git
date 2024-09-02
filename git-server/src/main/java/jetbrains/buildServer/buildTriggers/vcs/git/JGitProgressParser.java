

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class JGitProgressParser implements LineAwareByteArrayOutputStream.LineListener {

  private final GitProgress myProgress;

  public JGitProgressParser(@NotNull GitProgress progress) {
    myProgress = progress;
  }

  public void newLineDetected(@NotNull String line) {
    String trimmed = line.trim();
    if (isEmpty(trimmed))
      return;
    String separator = ": ";
    int separatorIdx = trimmed.lastIndexOf(separator);
    if (separatorIdx == -1)
      return;
    String stage = trimmed.substring(0, separatorIdx);
    String percents = trimmed.substring(separatorIdx + separator.length()).trim();
    if (percents.endsWith("%")) {
      try {
        int progress = Integer.parseInt(percents.substring(0, percents.length() - 1));
        myProgress.reportProgress(progress / 100.0f, stage);
      } catch (NumberFormatException e) {
        myProgress.reportProgress(-1, trimmed);
      }
    } else {
      myProgress.reportProgress(-1, trimmed);
    }
  }
}