

package jetbrains.buildServer.buildTriggers.vcs.git.command.errors;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class CheckoutCanceledException extends VcsException {

  public CheckoutCanceledException(@NotNull String reason) {
    super("Checkout canceled: " + reason);
  }
}