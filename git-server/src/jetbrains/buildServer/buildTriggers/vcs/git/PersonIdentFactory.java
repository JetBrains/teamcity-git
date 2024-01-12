

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;

/**
 * Methods for retrieving {@link PersonIdent}.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public class PersonIdentFactory {

  @NotNull
  public static PersonIdent getTagger(@NotNull final GitVcsRoot gitRoot, @NotNull final Repository r) {
    if (gitRoot.getUsernameForTags() != null) {
      return parseIdent(gitRoot.getUsernameForTags());
    }
    return new PersonIdent(r);
  }

  @NotNull
  public static PersonIdent parseIdent(@NotNull String ident) {
    int emailStartIdx = ident.indexOf("<");
    if (emailStartIdx == -1)
      return new PersonIdent(ident, "");
    int emailEndIdx = ident.lastIndexOf(">");
    if (emailEndIdx < emailStartIdx)
      return new PersonIdent(ident, "");
    String username = ident.substring(0, emailStartIdx).trim();
    String email = ident.substring(emailStartIdx + 1, emailEndIdx);
    return new PersonIdent(username, email);
  }
}