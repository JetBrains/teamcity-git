

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

public class WrongPassphraseException extends UnsupportedCredentialItem {

  public WrongPassphraseException(@NotNull URIish uri) {
    super(uri, "Wrong passphrase for selected SSH key");
  }
}