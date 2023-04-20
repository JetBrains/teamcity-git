

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper for working with JGit URIish objects. The class uses {@link CommonURIish} as URIish wrapper in porpoise to make the common
 * module do not depends on JGit library.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public interface URIishHelper {

  @Nullable
  String getUserNameFromUrl(final String url);

  CommonURIish createAuthURI(@NotNull final AuthSettings authSettings, @Nullable final String uri) throws VcsException;

  CommonURIish createAuthURI(@NotNull final AuthSettings authSettings, @Nullable final String uri, final boolean fixErrors) throws VcsException;

  CommonURIish createAuthURI(@NotNull final AuthSettings authSettings, @NotNull final CommonURIish uri);

  CommonURIish createAuthURI(@NotNull final AuthSettings authSettings, @NotNull final CommonURIish uri, final boolean fixErrors);

  CommonURIish createURI(@Nullable final String uri) throws VcsException;

  CommonURIish removeAuth(@NotNull final CommonURIish uri);
}