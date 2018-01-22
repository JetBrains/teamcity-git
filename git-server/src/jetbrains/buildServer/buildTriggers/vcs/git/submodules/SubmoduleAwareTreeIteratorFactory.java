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

import com.intellij.util.containers.IntArrayList;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.vcs.CheckoutRules;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.IOException;
import java.util.LinkedList;

/**
 * @author dmitry.neverov
 */
public class SubmoduleAwareTreeIteratorFactory {

  /**
   * Create a tree iterator from commit
   *
   * @param commit        a start commit
   * @param subResolver   a submodule resolver
   * @param repositoryUrl the url of the repository of this iterator
   * @param pathFromRoot  the path from the root of main repository to the entry of this repository
   * @return an iterator for tree that considers submodules
   * @throws java.io.IOException in the case if IO error occurs
   */
  public static SubmoduleAwareTreeIterator create(Repository db,
                                                  RevCommit commit,
                                                  SubmoduleResolverImpl subResolver,
                                                  String repositoryUrl,
                                                  String pathFromRoot,
                                                  SubmodulesCheckoutPolicy submodulePolicy,
                                                  boolean logSubmoduleErrors,
                                                  CheckoutRules rules)
    throws IOException {
    return createSubmoduleAwareTreeIterator(null, createTreeParser(db, commit), subResolver, "", repositoryUrl, pathFromRoot, submodulePolicy, logSubmoduleErrors, rules);
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
  public static SubmoduleAwareTreeIterator createSubmoduleAwareTreeIterator(SubmoduleAwareTreeIterator parent,
                                                                             AbstractTreeIterator wrapped,
                                                                             SubmoduleResolver subResolver,
                                                                             String path,
                                                                             String repositoryUrl,
                                                                             String pathFromRoot,
                                                                             SubmodulesCheckoutPolicy submodulesPolicy,
                                                                             boolean logSubmoduleErrors,
                                                                             CheckoutRules rules) throws IOException {
    SubmoduleAwareTreeIterator result;
    if (subResolver.containsSubmodule(path)) {
      int[] mapping = buildMapping(wrapped);
      String submoduleUrl = subResolver.getSubmoduleUrl(path);
      if (submoduleUrl == null)
        submoduleUrl = repositoryUrl;
      if (mapping == null) {
        result = parent == null
               ? new DirectSubmoduleAwareTreeIterator(wrapped, subResolver, submoduleUrl, pathFromRoot, submodulesPolicy, logSubmoduleErrors)
               : new DirectSubmoduleAwareTreeIterator(parent, wrapped, subResolver, submoduleUrl, pathFromRoot, submodulesPolicy, logSubmoduleErrors);
      } else {
        result = parent == null
               ? new IndirectSubmoduleAwareTreeIterator(wrapped, subResolver, mapping, submoduleUrl, pathFromRoot, submodulesPolicy, logSubmoduleErrors)
               : new IndirectSubmoduleAwareTreeIterator(parent, wrapped, subResolver, mapping, submoduleUrl, pathFromRoot, submodulesPolicy, logSubmoduleErrors);
      }
    } else {
      result = parent == null
           ? new DirectSubmoduleAwareTreeIterator(wrapped, subResolver, repositoryUrl, pathFromRoot, submodulesPolicy, logSubmoduleErrors)
           : new DirectSubmoduleAwareTreeIterator(parent, wrapped, subResolver, repositoryUrl, pathFromRoot, submodulesPolicy, logSubmoduleErrors);
    }
    result.setCheckoutRules(rules);
    return result;
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
      if (w.getEntryRawMode() == SubmoduleAwareTreeIterator.GITLINK_MODE_BITS) {
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
