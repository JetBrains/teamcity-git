/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;

import java.io.IOException;

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
   * @throws IOException in case of submodule processing problem
   */
  public DirectSubmoduleAwareTreeIterator(AbstractTreeIterator wrappedIterator,
                                          SubmoduleResolver submoduleResolver,
                                          String repositoryUrl, String pathFromRoot,
                                          SubmodulesCheckoutPolicy submodulesPolicy,
                                          boolean logSubmoduleErrors) throws IOException {
    super(wrappedIterator, submoduleResolver, repositoryUrl, pathFromRoot, submodulesPolicy, logSubmoduleErrors);
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
                                          boolean logSubmoduleErrors) throws IOException {
    super(parent, wrappedIterator, submoduleResolver, repositoryUrl, pathFromRoot, submodulesPolicy, logSubmoduleErrors);
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
