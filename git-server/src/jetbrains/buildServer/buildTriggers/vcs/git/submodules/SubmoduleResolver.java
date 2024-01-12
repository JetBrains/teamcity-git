

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;

public interface SubmoduleResolver {

  @NotNull
  RevCommit getSubmoduleCommit(@NotNull String parentRepositoryUrl,
                               @NotNull String path,
                               @NotNull ObjectId commit) throws IOException, VcsException, URISyntaxException;

  /**
   * Get submodule resolver for the path
   *
   * @param commit the start commit
   * @param path   the local path within repository
   * @return the submodule resolver that handles submodules inside the specified commit
   */
  SubmoduleResolver getSubResolver(RevCommit commit, String path);

  /**
   * Get repository by the URL. Note that the repository is retrieved but not cleaned up. This should be done by implementer of this component at later time.
   *
   * @param submoduleUrl the URL to resolve
   * @return the resolved repository
   * @throws jetbrains.buildServer.buildTriggers.vcs.git.VcsAuthenticationException in case of authentication problems
   * @throws java.net.URISyntaxException if there are errors in submodule repository URI
   */
  Repository resolveRepository(@NotNull String submoduleUrl) throws VcsException, URISyntaxException;

  void fetch(Repository r, String submodulePath, String submoduleUrl) throws VcsException, URISyntaxException, IOException;

  URIish resolveSubmoduleUrl(@NotNull String submoduleUrl) throws URISyntaxException;

  boolean containsSubmodule(String path);

  Repository getRepository();

  String getSubmoduleUrl(String submodulePath);
}