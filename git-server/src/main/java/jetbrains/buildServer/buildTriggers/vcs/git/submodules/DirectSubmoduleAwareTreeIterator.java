

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.SubmoduleAwareTreeIterator;

import java.io.IOException;
import org.jetbrains.annotations.Nullable;

/**
 * Direct submodule-aware tree iterator. This iterator for the cases when no directory entry reordering is needed.
 */
public class DirectSubmoduleAwareTreeIterator extends SubmoduleAwareTreeIterator {
  /**
   * The constructor
   *
   * @param wrappedIterator   the wrapped iterator
   * @param submoduleResolver the resolver for submodules
   * @param repositoryUrl     the url of the repository of this iterator
   * @param pathFromRoot      the path from the root of main repository to the entry of this repository
   * @param submodulesPolicy  should created iterator checkout submodules
   * @param missingSubmoduleCommitInfo information about missing commits in submodules
   * @throws IOException in case of submodule processing problem
   */
  public DirectSubmoduleAwareTreeIterator(AbstractTreeIterator wrappedIterator,
                                          SubmoduleResolver submoduleResolver,
                                          String repositoryUrl, String pathFromRoot,
                                          SubmodulesCheckoutPolicy submodulesPolicy,
                                          @Nullable MissingSubmoduleCommitInfo missingSubmoduleCommitInfo,
                                          boolean logSubmoduleErrors) throws IOException {
    super(wrappedIterator, submoduleResolver, repositoryUrl, pathFromRoot, submodulesPolicy, missingSubmoduleCommitInfo, logSubmoduleErrors);
  }

  /**
   * The constructor
   *
   * @param parent            the parent iterator
   * @param wrappedIterator   the wrapped iterator
   * @param submoduleResolver the resolver for submodules
   * @param repositoryUrl     the url of the repository of this iterator
   * @param pathFromRoot      the path from the root of main repository to the entry of this repository
   * @param submodulesPolicy  should created iterator checkout submodules
   * @throws IOException in case of submodule processing problem
   */
  public DirectSubmoduleAwareTreeIterator(SubmoduleAwareTreeIterator parent,
                                          AbstractTreeIterator wrappedIterator,
                                          SubmoduleResolver submoduleResolver,
                                          String repositoryUrl,
                                          String pathFromRoot,
                                          SubmodulesCheckoutPolicy submodulesPolicy,
                                          @Nullable MissingSubmoduleCommitInfo missingSubmoduleCommitInfo,
                                          boolean logSubmoduleErrors) throws IOException {
    super(parent, wrappedIterator, submoduleResolver, repositoryUrl, pathFromRoot, submodulesPolicy, missingSubmoduleCommitInfo, logSubmoduleErrors);
  }

  /**
   * {@inheritDoc}
   */
  public boolean first() {
    return myWrappedIterator.first();
  }

  /**
   * {@inheritDoc}
   */
  public boolean eof() {
    return myWrappedIterator.eof();
  }

  /**
   * {@inheritDoc}
   */
  public void next(int delta) throws CorruptObjectException {
    myWrappedIterator.next(delta);
    movedToEntry();
  }

  /**
   * {@inheritDoc}
   */
  public void back(int delta) throws CorruptObjectException {
    myWrappedIterator.back(delta);
    movedToEntry();
  }
}