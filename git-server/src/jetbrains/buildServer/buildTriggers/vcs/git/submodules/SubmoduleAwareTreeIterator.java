/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.util.containers.IntArrayList;
import org.spearce.jgit.errors.CorruptObjectException;
import org.spearce.jgit.lib.*;
import org.spearce.jgit.treewalk.AbstractTreeIterator;
import org.spearce.jgit.treewalk.CanonicalTreeParser;

import java.io.IOException;
import java.util.LinkedList;

/**
 * The tree iterator that aware of the submodules. If submodule entry
 * is encountered, it is replaced with referenced tree.
 */
public abstract class SubmoduleAwareTreeIterator extends AbstractTreeIterator {
  /**
   * The iterator wrapped by this iterator
   */
  protected final AbstractTreeIterator myWrappedIterator;
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
  protected Commit mySubmoduleCommit;
  /**
   * Submodule reference mode bits
   */
  protected static final int GITLINK_MODE_BITS = FileMode.GITLINK.getBits();
  /**
   * Tree mode bits
   */
  protected static final int TREE_MODE_BITS = FileMode.TREE.getBits();

  /**
   * The constructor
   *
   * @param wrappedIterator   the wrapped iterator
   * @param submoduleResolver the resolver for submodules
   * @throws CorruptObjectException in case of submodule processing problem
   */
  public SubmoduleAwareTreeIterator(AbstractTreeIterator wrappedIterator, SubmoduleResolver submoduleResolver)
    throws CorruptObjectException {
    myWrappedIterator = wrappedIterator;
    mySubmoduleResolver = submoduleResolver;
    movedToEntry();
  }

  /**
   * The constructor
   *
   * @param commit            the commit that is starting point for iteration
   * @param submoduleResolver the resolver for submodules
   * @throws IOException in case of IO problem
   */
  public SubmoduleAwareTreeIterator(Commit commit, SubmoduleResolver submoduleResolver) throws IOException {
    this(createTreeParser(commit), submoduleResolver);
  }


  /**
   * The constructor
   *
   * @param parent            the parent iterator
   * @param wrappedIterator   the wrapped iterator
   * @param submoduleResolver the resolver for submodules
   * @throws CorruptObjectException in case of submodule processing problem
   */
  public SubmoduleAwareTreeIterator(SubmoduleAwareTreeIterator parent,
                                    AbstractTreeIterator wrappedIterator,
                                    SubmoduleResolver submoduleResolver)
    throws CorruptObjectException {
    super(parent);
    myWrappedIterator = wrappedIterator;
    mySubmoduleResolver = submoduleResolver;
    movedToEntry();
  }

  /**
   * The constructor
   *
   * @param parent            the parent iterator
   * @param commit            the commit that is starting point for iteration
   * @param submoduleResolver the resolver for submodules
   * @throws IOException in case of IO problem
   */
  public SubmoduleAwareTreeIterator(SubmoduleAwareTreeIterator parent, Commit commit, SubmoduleResolver submoduleResolver)
    throws IOException {
    this(parent, createTreeParser(commit), submoduleResolver);
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
    myIsOnSubmodule = GITLINK_MODE_BITS == wrappedMode;
    mode = myIsOnSubmodule ? TREE_MODE_BITS : wrappedMode;
    if (myIsOnSubmodule) {
      try {
        mySubmoduleCommit = mySubmoduleResolver.getSubmodule(myWrappedIterator.getEntryPathString(), myWrappedIterator.getEntryObjectId());
      } catch (IOException e) {
        final CorruptObjectException ex = new CorruptObjectException(myWrappedIterator.getEntryObjectId(), "Commit could not be resolved");
        ex.initCause(e);
        throw ex;
      }
      if (myIdBuffer == null) {
        myIdBuffer = new byte[Constants.OBJECT_ID_LENGTH];
      }
      mySubmoduleCommit.getTreeId().copyRawTo(myIdBuffer, 0);
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

  /**
   * {@inheritDoc}
   */
  @Override
  public AbstractTreeIterator createSubtreeIterator(Repository repo, MutableObjectId idBuffer, WindowCursor curs)
    throws IOException {
    String path = myWrappedIterator.getEntryPathString();
    if (myIsOnSubmodule) {
      CanonicalTreeParser p = createTreeParser(curs, mySubmoduleCommit);
      return createSubmoduleAwareTreeIterator(this, p, mySubmoduleResolver.getSubResolver(mySubmoduleCommit, path), "");
    } else {
      return createSubmoduleAwareTreeIterator(this, myWrappedIterator.createSubtreeIterator(getRepository(), idBuffer, curs),
                                              mySubmoduleResolver,
                                              path);
    }
  }


  /**
   * Create a tree iterator from commit
   *
   * @param commit      a start commit
   * @param subResolver a submodule resolver
   * @return an iterator for tree that considers submodules
   * @throws IOException in the case if IO error occurs
   */
  public static SubmoduleAwareTreeIterator create(Commit commit, SubmoduleResolver subResolver)
    throws IOException {
    return createSubmoduleAwareTreeIterator(null, createTreeParser(commit), subResolver, "");
  }


  /**
   * Create a tree iterator from commit
   *
   * @param parent      the parent iterator (or null)
   * @param wrapped     the wrapped iterator
   * @param subResolver a submodule resolver
   * @param path        the path the submodule is referenced in the local repository
   * @return an iterator for tree that considers submodules
   * @throws IOException in the case if IO error occurs
   */
  private static SubmoduleAwareTreeIterator createSubmoduleAwareTreeIterator(SubmoduleAwareTreeIterator parent,
                                                                             AbstractTreeIterator wrapped,
                                                                             SubmoduleResolver subResolver, String path)
    throws IOException {
    if (subResolver.containsSubmodule(path)) {
      int[] mapping = buildMapping(wrapped);
      if (mapping == null) {
        return parent == null
               ? new DirectSubmoduleAwareTreeIterator(wrapped, subResolver)
               : new DirectSubmoduleAwareTreeIterator(parent, wrapped, subResolver);
      } else {
        return parent == null
               ? new IndirectSubmoduleAwareTreeIterator(wrapped, subResolver, mapping)
               : new IndirectSubmoduleAwareTreeIterator(parent, wrapped, subResolver, mapping);
      }
    }
    return parent == null
           ? new DirectSubmoduleAwareTreeIterator(wrapped, subResolver)
           : new DirectSubmoduleAwareTreeIterator(parent, wrapped, subResolver);
  }

  /**
   * {@inheritDoc}
   */
  public AbstractTreeIterator createSubtreeIterator(Repository repo) throws IOException {
    String path = myWrappedIterator.getEntryPathString();
    if (myIsOnSubmodule) {
      WindowCursor curs = new WindowCursor();
      try {
        CanonicalTreeParser p = createTreeParser(curs, mySubmoduleCommit);
        return createSubmoduleAwareTreeIterator(this, p, mySubmoduleResolver.getSubResolver(mySubmoduleCommit, path), "");
      } finally {
        curs.release();
      }
    } else {
      return createSubmoduleAwareTreeIterator(this, myWrappedIterator.createSubtreeIterator(getRepository()), mySubmoduleResolver,
                                              path);
    }
  }

  /**
   * Create tree parser
   *
   * @param curs   the window cursor for loading objects
   * @param commit the commit
   * @return the tree parser for tree in the commit
   * @throws IOException in case of IO problem
   */
  private static CanonicalTreeParser createTreeParser(WindowCursor curs, final Commit commit) throws IOException {
    CanonicalTreeParser p = new CanonicalTreeParser();
    p.reset(commit.getRepository(), commit.getTreeId(), curs);
    return p;
  }


  /**
   * Create tree parser
   *
   * @param commit the commit that contains point ot the tree
   * @return the tree parser
   * @throws IOException in case of IO problem
   */
  public static CanonicalTreeParser createTreeParser(final Commit commit) throws IOException {
    WindowCursor curs = new WindowCursor();
    try {
      return createTreeParser(curs, commit);
    } finally {
      curs.release();
    }
  }

  /**
   * Scan current tree and build mapping that reorders submodule entries if needed.
   *
   * @param w wrapped tree iterator
   * @return a mapping or null if the mapping is not needed
   * @throws CorruptObjectException in case if navigation fails
   */
  static int[] buildMapping(final AbstractTreeIterator w) throws CorruptObjectException {
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
