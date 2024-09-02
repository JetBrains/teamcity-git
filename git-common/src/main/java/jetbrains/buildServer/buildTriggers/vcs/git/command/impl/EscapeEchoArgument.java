

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface EscapeEchoArgument {

  @NotNull
  String escape(@Nullable String s);

}