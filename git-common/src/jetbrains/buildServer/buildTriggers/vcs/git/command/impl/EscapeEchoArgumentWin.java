

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EscapeEchoArgumentWin implements EscapeEchoArgument {

  @NotNull
  public String escape(@Nullable String s) {
    if (s == null)
      s = "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&':
        case '^':
        case '<':
        case '>':
        case '|':
        case '"':
          sb.append("^");
          sb.append(c);
          break;
        case '%':
          sb.append("%");
          sb.append(c);
          break;
        default:
          sb.append(c);
          break;
      }
    }
    return sb.toString();
  }

}