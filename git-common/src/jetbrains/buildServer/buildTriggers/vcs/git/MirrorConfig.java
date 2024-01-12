

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface MirrorConfig {

  @NotNull
  File getCachesDir();

}