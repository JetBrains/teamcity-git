

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of JGit's {@link CredentialsProvider}.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public class AuthCredentialsProvider extends CredentialsProvider {

  private final AuthenticationMethod myAuthMethod;
  private final String myUserName;
  private final String myPassword;

  public AuthCredentialsProvider(@NotNull final AuthSettings authSettings) {
    myAuthMethod = authSettings.getAuthMethod();
    myUserName = authSettings.getUserName();
    myPassword = authSettings.getPassword();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isInteractive() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supports(final CredentialItem... items) {
    for (CredentialItem i : items) {
      if (i instanceof CredentialItem.Username && myAuthMethod != AuthenticationMethod.ANONYMOUS) {
        continue;
      }
      if (i instanceof CredentialItem.Password && myAuthMethod.isPasswordBased() && myPassword != null) {
        continue;
      }
      return false;
    }
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean get(final URIish uri, final CredentialItem... items) throws UnsupportedCredentialItem {
    boolean allValuesSupplied = true;
    for (CredentialItem i : items) {
      if (i instanceof CredentialItem.Username) {
        allValuesSupplied &= supplyUsername(uri, (CredentialItem.Username)i);
      } else if (i instanceof CredentialItem.Password) {
        allValuesSupplied &= supplyPassword((CredentialItem.Password)i);
      } else if (i instanceof CredentialItem.StringType && "Passphrase for ".equals(i.getPromptText())) {
        //we provider a passphrase to the jsch, if we are asked about it
        //then the original passphrase was incorrect
        throw new WrongPassphraseException(uri);
      } else {
        throw new UnsupportedCredentialItem(uri, i.getPromptText());
      }
    }
    return allValuesSupplied;
  }

  private boolean supplyUsername(URIish uri, CredentialItem.Username item) {
    if (myAuthMethod == AuthenticationMethod.ANONYMOUS) {
      return false;
    }
    String username = myUserName != null ? myUserName : uri.getUser();
    if (username == null) {
      return false;
    }
    item.setValue(username);
    return true;
  }

  private boolean supplyPassword(CredentialItem.Password item) {
    if (!myAuthMethod.isPasswordBased()) {
      return false;
    }
    if (myPassword == null) {
      return false;
    }
    item.setValue(myPassword.toCharArray());
    return true;
  }
}