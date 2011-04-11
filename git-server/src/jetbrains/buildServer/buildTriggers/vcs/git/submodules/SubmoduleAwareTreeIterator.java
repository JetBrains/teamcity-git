/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.containers.IntArrayList;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.VcsAuthenticationException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.IOException;
import java.util.LinkedList;

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
   * URL of repository for this iterator, used in error messages
   */
  private final String myUrl;
  /**
   * Path from root of the main repository to the entry of repository of this iterator, used in error messages.
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
                                    SubmodulesCheckoutPolicy submodulesPolicy)
    throws CorruptObjectException {
    myWrappedIterator = wrappedIterator;
    mySubmoduleResolver = submoduleResolver;
    myUrl = repositoryUrl;
    myPathFromRoot = pathFromRoot;
    mySubmodulesPolicy = submodulesPolicy;
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
                                    SubmodulesCheckoutPolicy submodulesPolicy)
    throws CorruptObjectException {
    super(parent);
    myParent = parent;
    myWrappedIterator = wrappedIterator;
    mySubmoduleResolver = submoduleResolver;
    myUrl = repositoryUrl;
    myPathFromRoot = pathFromRoot;
    mySubmodulesPolicy = submodulesPolicy;
    movedToEntry();
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
    myIsOnSubmodule = checkoutSubmodules() && GITLINK_MODE_BITS == wrappedMode;
    mode = myIsOnSubmodule ? TREE_MODE_BITS : wrappedMode;
    if (myIsOnSubmodule) {
      String entryPath = myWrappedIterator.getEntryPathString();
      try {
        mySubmoduleCommit = getSubmoduleCommit(entryPath, myWrappedIterator.getEntryObjectId());
      } catch (CorruptObjectException e) {
        if (mySubmodulesPolicy.isIgnoreSubmodulesErrors()) {
          //pretend that this is simple directory, not the submodule
          LOG.warn("Ignore submodule error: \"" + e.getMessage() + "\". It seems to be fixed in one of the later commits.");
          mySubmoduleCommit = null;
          myIsOnSubmodule = false;
          mySubmoduleError = true;
          mode = wrappedMode;
        } else {
          throw e;
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

  private RevCommit getSubmoduleCommit(String path, ObjectId entryObjectId) throws CorruptObjectException {
    try {
      return mySubmoduleResolver.getSubmoduleCommit(path, entryObjectId);
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
    } catch (IOException e) {
      final CorruptObjectException ex = new CorruptObjectException(entryObjectId, "Commit could not be resolved");
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
        Repository r = mySubmoduleResolver.resolveRepository(path, mySubmoduleResolver.getSubmoduleUrl(path));
        or = r.newObjectReader();
        p.reset(or, mySubmoduleCommit.getTree().getId());
      } catch (VcsAuthenticationException e) {
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
                                              SubmodulesCheckoutPolicy.getSubSubModulePolicyFor(mySubmodulesPolicy));
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
                                              mySubmodulesPolicy);
    }
  }

  /**
   * Create a tree iterator from commit
   *
   * @param commit        a start commit
   * @param subResolver   a submodule resolver
   * @param repositoryUrl the url of the repository of this iterator
   * @param pathFromRoot  the path from the root of main repository to the entry of this repository
   * @return an iterator for tree that considers submodules
   * @throws IOException in the case if IO error occurs
   */
  public static SubmoduleAwareTreeIterator create(Repository db,
                                                  RevCommit commit,
                                                  SubmoduleResolver subResolver,
                                                  String repositoryUrl,
                                                  String pathFromRoot,
                                                  SubmodulesCheckoutPolicy submodulePolicy)
    throws IOException {
    return createSubmoduleAwareTreeIterator(null, createTreeParser(db, commit), subResolver, "", repositoryUrl, pathFromRoot, submodulePolicy);
  }


  /**
   * Create a tree iterator from commit
   *
   * @param parent             the parent iterator (or null)
   * @param wrapped            the wrapped iterator
   * @param subResolver        a submodule resolver
   * @param path               the path the submodule is referenced in the local repository
   * @param repositoryUrl      the url of the repository of this iterator
   * @param pathFromRoot       the path from the root of main repository to the entry of this repository
   * @param submodulesPolicy   submodule checkout policy
   * @return an iterator for tree that considers submodules
   * @throws IOException in the case if IO error occurs
   */
  private static SubmoduleAwareTreeIterator createSubmoduleAwareTreeIterator(SubmoduleAwareTreeIterator parent,
                                                                             AbstractTreeIterator wrapped,
                                                                             SubmoduleResolver subResolver,
                                                                             String path,
                                                                             String repositoryUrl,
                                                                             String pathFromRoot,
                                                                             SubmodulesCheckoutPolicy submodulesPolicy) throws IOException {
    if (subResolver.containsSubmodule(path)) {
      int[] mapping = buildMapping(wrapped);
      String submoduleUrl = subResolver.getSubmoduleUrl(path);
      if (submoduleUrl == null)
        submoduleUrl = repositoryUrl;
      if (mapping == null) {
        return parent == null
               ? new DirectSubmoduleAwareTreeIterator(wrapped, subResolver, submoduleUrl, pathFromRoot, submodulesPolicy)
               : new DirectSubmoduleAwareTreeIterator(parent, wrapped, subResolver, submoduleUrl, pathFromRoot, submodulesPolicy);
      } else {
        return parent == null
               ? new IndirectSubmoduleAwareTreeIterator(wrapped, subResolver, mapping, submoduleUrl, pathFromRoot, submodulesPolicy)
               : new IndirectSubmoduleAwareTreeIterator(parent, wrapped, subResolver, mapping, submoduleUrl, pathFromRoot, submodulesPolicy);
      }
    }
    return parent == null
           ? new DirectSubmoduleAwareTreeIterator(wrapped, subResolver, repositoryUrl, pathFromRoot, submodulesPolicy)
           : new DirectSubmoduleAwareTreeIterator(parent, wrapped, subResolver, repositoryUrl, pathFromRoot, submodulesPolicy);
  }

  @Override
  public boolean hasId() {
    return true;
  }

  private static CanonicalTreeParser createTreeParser(final Repository db, final RevCommit commit) throws IOException {
    ObjectReader reader = db.newObjectReader();
    try {
      CanonicalTreeParser parser = new CanonicalTreeParser();
      parser.reset(reader, commit.getTree().getId());
      return parser;
    } finally {
      reader.release();
    }
  }

  /**
   * Scan current tree and build mapping that reorders submodule entries if needed.
   *
   * @param w wrapped tree iterator
   * @return a mapping or null if the mapping is not needed
   * @throws CorruptObjectException in case if navigation fails
   */
  private static int[] buildMapping(final AbstractTreeIterator w) throws CorruptObjectException {
    class SubmoduleEntry {
      final ByteRange name;
      final int position;

      public SubmoduleEntry(int position) {
        this.position = position;
        byte[] n = new byte[w.getNameLength() + 1];
        w.getName(n, 0);
        n[n.length - 1] = '/';
        name = new ByteRange(n);
      }
    }
    IntArrayList rc = new IntArrayList();
    boolean reordered = false;
    LinkedList<SubmoduleEntry> stack = new LinkedList<SubmoduleEntry>();
    int actual = 0;
    assert w.first();
    if (w.eof()) {
      return null;
    }
    final int INITIAL_NAME_SIZE = 32;
    byte[] name = new byte[INITIAL_NAME_SIZE];
    while (!w.eof()) {
      if (!stack.isEmpty()) {
        int l = w.getNameLength();
        if (l > name.length) {
          int nl = name.length;
          while (nl < l) {
            nl <<= 1;
          }
          name = new byte[nl];
        }
        w.getName(name, 0);
        ByteRange currentName = new ByteRange(name, 0, l);
        while (!stack.isEmpty()) {
          final SubmoduleEntry top = stack.getLast();
          final int result = top.name.compareTo(currentName);
          assert result != 0;
          if (result < 0) {
            if (top.position != rc.size()) {
              reordered = true;
            }
            rc.add(top.position);
            stack.removeLast();
          } else {
            break;
          }
        }
      }
      if (w.getEntryRawMode() == GITLINK_MODE_BITS) {
        stack.add(new SubmoduleEntry(actual));
      } else {
        rc.add(actual);
      }
      w.next(1);
      actual++;
    }
    while (!stack.isEmpty()) {
      final SubmoduleEntry top = stack.removeLast();
      if (top.position != rc.size()) {
        reordered = true;
      }
      rc.add(top.position);
    }
    w.back(actual);
    assert w.first();
    return reordered ? rc.toArray() : null;
  }
}
