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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.VcsAuthenticationException;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;

import static jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleAwareTreeIteratorFactory.createSubmoduleAwareTreeIterator;

/**
 * The tree iterator that aware of the submodules. If submodule entry
 * is encountered, it is replaced with referenced tree.
 */
public abstract class SubmoduleAwareTreeIterator extends AbstractTreeIterator {

  private static Logger LOG = Logger.getInstance(SubmoduleAwareTreeIterator.class.getName());
  /**
   * The iterator wrapped by this iterator
   */
  protected final AbstractTreeIterator myWrappedIterator;
  /**
   * URL of repository for this iterator
   */
  private final String myUrl;
  /**
   * Path from root of the main repository to the entry of repository of this iterator.
   * For main repository it is equals "", for repository of submodule it is equals to submodule path,
   * for sub-submodule path of parent submodule + path of current submodule and so on.
   */
  private final String myPathFromRoot;
  /**
   * Policy for submodules
   */
  private SubmodulesCheckoutPolicy mySubmodulesPolicy;
  /**
   * The resolver for submodules
   */
  protected final SubmoduleResolver mySubmoduleResolver;
  /**
   * The local id buffer (for submodules)
   */
  protected byte[] myIdBuffer;
  /**
   * If true the current entry is as submodule entry
   */
  protected boolean myIsOnSubmodule;
  /**
   * If true, the iterator is on EOF
   */
  protected boolean myIsEof;
  /**
   * The referenced commit for the submodule, the commit is in other repository.
   */
  protected RevCommit mySubmoduleCommit;

  private boolean mySubmoduleError;
  /**
   * Submodule reference mode bits
   */
  protected static final int GITLINK_MODE_BITS = FileMode.GITLINK.getBits();
  /**
   * Tree mode bits
   */
  protected static final int TREE_MODE_BITS = FileMode.TREE.getBits();
  private SubmoduleAwareTreeIterator myParent;

  private final boolean myLogSubmoduleErrors;

  //submodules excluded by the these rules will not be resolved; null means resolve all submodules
  private CheckoutRules myRules;

  /**
   * The constructor
   *
   * @param wrappedIterator   the wrapped iterator
   * @param submoduleResolver the resolver for submodules
   * @param repositoryUrl     the url of the repository of this iterator
   * @param pathFromRoot      the path from the root of main repository to the entry of this repository
   * @param submodulesPolicy  should iterator checkout submodules
   * @throws CorruptObjectException in case of submodule processing problem
   */
  public SubmoduleAwareTreeIterator(AbstractTreeIterator wrappedIterator,
                                    SubmoduleResolver submoduleResolver,
                                    String repositoryUrl,
                                    String pathFromRoot,
                                    SubmodulesCheckoutPolicy submodulesPolicy,
                                    boolean logSubmoduleErrors)
    throws CorruptObjectException {
    myWrappedIterator = wrappedIterator;
    mySubmoduleResolver = submoduleResolver;
    myUrl = repositoryUrl;
    myPathFromRoot = pathFromRoot;
    mySubmodulesPolicy = submodulesPolicy;
    myLogSubmoduleErrors = logSubmoduleErrors;
    movedToEntry();
  }

  /**
   * The constructor
   *
   * @param parent            the parent iterator
   * @param wrappedIterator   the wrapped iterator
   * @param submoduleResolver the resolver for submodules
   * @param repositoryUrl     the url of the repository of this iterator
   * @param pathFromRoot      the path from the root of main repository to the entry of this repository
   * @param submodulesPolicy  should iterator checkout submodules
   * @throws CorruptObjectException in case of submodule processing problem
   */
  public SubmoduleAwareTreeIterator(SubmoduleAwareTreeIterator parent,
                                    AbstractTreeIterator wrappedIterator,
                                    SubmoduleResolver submoduleResolver,
                                    String repositoryUrl,
                                    String pathFromRoot,
                                    SubmodulesCheckoutPolicy submodulesPolicy,
                                    boolean logSubmoduleErrors)
    throws CorruptObjectException {
    super(parent);
    myParent = parent;
    myWrappedIterator = wrappedIterator;
    mySubmoduleResolver = submoduleResolver;
    myUrl = repositoryUrl;
    myPathFromRoot = pathFromRoot;
    mySubmodulesPolicy = submodulesPolicy;
    myLogSubmoduleErrors = logSubmoduleErrors;
    movedToEntry();
  }


  void setCheckoutRules(CheckoutRules rules) {
    myRules = rules;
  }

  /**
   * @return the current repository for the submodule
   */
  public Repository getRepository() {
    return mySubmoduleResolver.getRepository();
  }


  /**
   * Move iterator to the specific entry.
   *
   * @throws CorruptObjectException in case of submodule processing problem
   */
  protected void movedToEntry() throws CorruptObjectException {
    myIsEof = eof();
    if (myIsEof) {
      return;
    }
    int wrappedMode = myWrappedIterator.getEntryRawMode();
    String entryPath = myWrappedIterator.getEntryPathString();
    myIsOnSubmodule = checkoutSubmodules() && GITLINK_MODE_BITS == wrappedMode;
    if (myIsOnSubmodule && myRules != null) {
      //if submodule dir is excluded by checkout rules we can treat it as an empty dir
      String pathFromRoot = getPathFromRoot(entryPath);
      if (myRules.map(pathFromRoot) == null) {
        //submodule dir itself is excluded, but some of its dirs can be included
        //happens with checkout rules like +:submodule/dir1
        boolean rulesInsideSubmodule = false;
        CheckoutRules submoduleAsRule = new CheckoutRules("+:" + pathFromRoot);
        for (IncludeRule rule : myRules.getRootIncludeRules()) {
          if (submoduleAsRule.map(rule.getFrom()) != null) {
            rulesInsideSubmodule = true;
            break;
          }
        }
        if (!rulesInsideSubmodule) {
          myIsOnSubmodule = false;
        }
      }
    }
    mode = myIsOnSubmodule ? TREE_MODE_BITS : wrappedMode;
    if (myIsOnSubmodule) {
      try {
        mySubmoduleCommit = getSubmoduleCommit(entryPath, myWrappedIterator.getEntryObjectId());
      } catch (Exception e) {
        if (mySubmodulesPolicy.isIgnoreSubmodulesErrors()) {
          if (myLogSubmoduleErrors)
            LOG.warn("Ignore submodule error: \"" + e.getMessage() + "\". It seems to be fixed in one of the later commits.");
          mySubmoduleCommit = null;
          myIsOnSubmodule = false;
          mySubmoduleError = true;
          mode = wrappedMode;
        } else {
          if (e instanceof CorruptObjectException) {
            throw (CorruptObjectException) e;
          } else {
            CorruptObjectException ex = new CorruptObjectException(myWrappedIterator.getEntryObjectId(), e.getMessage());
            ex.initCause(e);
            throw ex;
          }
        }
      }
      if (myIdBuffer == null) {
        myIdBuffer = new byte[Constants.OBJECT_ID_LENGTH];
      }
      if (mySubmoduleCommit != null) {
        mySubmoduleCommit.getTree().getId().copyRawTo(myIdBuffer, 0);
      }
    } else {
      mySubmoduleCommit = null;
    }
    // copy name
    final int nameLength = myWrappedIterator.getNameLength();
    final int pathLength = nameLength + pathOffset;
    ensurePathCapacity(pathLength, pathOffset);
    myWrappedIterator.getName(path, pathOffset);
    pathLen = pathLength;
  }

  public boolean isSubmoduleError() {
    return mySubmoduleError;
  }

  public SubmoduleAwareTreeIterator getParent() {
    return myParent;
  }

  public boolean isOnSubmodule() {
    return myIsOnSubmodule;
  }

  private RevCommit getSubmoduleCommit(@NotNull String path, @NotNull ObjectId entryObjectId) throws CorruptObjectException, VcsException, URISyntaxException {
    try {
      return mySubmoduleResolver.getSubmoduleCommit(myUrl, path, entryObjectId);
    } catch (VcsAuthenticationException e) {
      //in case of VcsAuthenticationException throw CorruptObjectException without object id,
      //because problem is related to whole repository, not to concrete object
      final SubmoduleFetchException ex = new SubmoduleFetchException(myUrl, path, getPathFromRoot(path));
      ex.initCause(e);
      throw ex;
    } catch (TransportException e) {
      //this problem is also related to whole repository
      final SubmoduleFetchException ex = new SubmoduleFetchException(myUrl, path, getPathFromRoot(path));
      ex.initCause(e);
      throw ex;
    } catch (CorruptObjectException e) {
      throw e;
    } catch (IOException e) {
      final CorruptObjectException ex = new CorruptObjectException(entryObjectId, "Commit could not be resolved: " + e.getMessage());
      ex.initCause(e);
      throw ex;
    }
  }

  /**
   * Check if this iterator should checkout found submodules
   * @return true if this iterator should checkout found submodules, false otherwise
   */
  private boolean checkoutSubmodules() {
    return mySubmodulesPolicy.equals(SubmodulesCheckoutPolicy.CHECKOUT) ||
           mySubmodulesPolicy.equals(SubmodulesCheckoutPolicy.CHECKOUT_IGNORING_ERRORS) ||
           mySubmodulesPolicy.equals(SubmodulesCheckoutPolicy.NON_RECURSIVE_CHECKOUT) ||
           mySubmodulesPolicy.equals(SubmodulesCheckoutPolicy.NON_RECURSIVE_CHECKOUT_IGNORING_ERRORS);
  }

  @NotNull
  private String getPathFromRoot(String path) {
    if ("".equals(myPathFromRoot) || myPathFromRoot.endsWith("/") || path.startsWith("/")) {
      return myPathFromRoot + path;
    } else {
      return myPathFromRoot + "/" + path;
    }
  }

  /**
   * {@inheritDoc}
   */
  public byte[] idBuffer() {
    if (myIsOnSubmodule) {
      return myIdBuffer;
    } else {
      return myWrappedIterator.idBuffer();
    }
  }

  /**
   * {@inheritDoc}
   */
  public int idOffset() {
    return myIsOnSubmodule ? 0 : myWrappedIterator.idOffset();
  }

  @Override
  public AbstractTreeIterator createSubtreeIterator(ObjectReader reader) throws IOException {
    String path = myWrappedIterator.getEntryPathString();
    if (myIsOnSubmodule) {
      CanonicalTreeParser p = new CanonicalTreeParser();
      ObjectReader or = null;
      try {
        Repository r = mySubmoduleResolver.resolveRepository(mySubmoduleResolver.getSubmoduleUrl(path));
        or = r.newObjectReader();
        p.reset(or, mySubmoduleCommit.getTree().getId());
      } catch (Exception e) {
        if (e instanceof IOException)
          throw (IOException) e;
        IOException ioe = new IOException("Submodule error");
        ioe.initCause(e);
        throw ioe;
      } finally {
        if (or != null) or.release();
      }
      return createSubmoduleAwareTreeIterator(this,
                                              p,
                                              mySubmoduleResolver.getSubResolver(mySubmoduleCommit, path),
                                              "",
                                              mySubmoduleResolver.getSubmoduleUrl(path),
                                              getPathFromRoot(path),
                                              SubmodulesCheckoutPolicy.getSubSubModulePolicyFor(mySubmodulesPolicy),
                                              myLogSubmoduleErrors,
                                              myRules);
    } else {
      Repository r = mySubmoduleResolver.getRepository();
      ObjectReader or = r.newObjectReader();
      AbstractTreeIterator ati = null;
      try {
        ati = myWrappedIterator.createSubtreeIterator(or);
      } finally {
        or.release();
      }
      return createSubmoduleAwareTreeIterator(this,
                                              ati,
                                              mySubmoduleResolver,
                                              path,
                                              myUrl,
                                              myPathFromRoot,
                                              mySubmodulesPolicy,
                                              myLogSubmoduleErrors,
                                              myRules);
    }
  }

  @Override
  public boolean hasId() {
    return true;
  }
}
