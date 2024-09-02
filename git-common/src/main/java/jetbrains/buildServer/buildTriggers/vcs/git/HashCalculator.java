

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public interface HashCalculator {

  long getHash(@NotNull String value);

}