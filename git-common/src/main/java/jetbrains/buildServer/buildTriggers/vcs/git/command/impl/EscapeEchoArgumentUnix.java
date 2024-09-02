

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EscapeEchoArgumentUnix implements EscapeEchoArgument {

  @NotNull
  public String escape(@Nullable String s) {
    if (s == null)
      s = "";
    StringBuilder sb = new StringBuilder();
    sb.append("'");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\'':
          //we cannot escape ' inside 'string', we have to close 'string',
          //add escaped ' and open next 'string'
          sb.append("'\\''");
          break;
        default:
          sb.append(c);
          break;
      }
    }
    sb.append("'");
    return sb.toString();
  }

}