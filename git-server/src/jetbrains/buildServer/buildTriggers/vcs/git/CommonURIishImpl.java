

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.eclipse.jgit.transport.URIish;

/**
 * Implementation of {@link CommonURIish}.
 * The same as in the agent module.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public class CommonURIishImpl implements CommonURIish {

  private final URIish myURIish;

  public CommonURIishImpl(final URIish urIish) {
    myURIish = urIish;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T get() {
    return (T)myURIish;
  }

  @Override
  public String getScheme() {
    return myURIish.getScheme();
  }

  @Override
  public String getHost() {
    return myURIish.getHost();
  }

  @Override
  public String toASCIIString() {
    return myURIish.toASCIIString();
  }

  @Override
  public String getPath() {
    return myURIish.getPath();
  }

  @Override
  public String getUser() {
    return myURIish.getUser();
  }

  @Override
  public int hashCode() {
    return myURIish.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof CommonURIish))
      return false;
    final CommonURIishImpl other = (CommonURIishImpl) obj;

    return myURIish.equals(other.get());
  }

  @Override
  public String toString() {
    return myURIish.toString();
  }
}