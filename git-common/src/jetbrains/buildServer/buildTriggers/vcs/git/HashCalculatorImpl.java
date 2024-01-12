

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class HashCalculatorImpl implements HashCalculator {

  public long getHash(@NotNull String value) {
    return value.hashCode();
  }

}