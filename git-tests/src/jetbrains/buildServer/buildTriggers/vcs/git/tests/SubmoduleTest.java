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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.*;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import org.spearce.jgit.lib.BlobBasedConfig;
import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.FileBasedConfig;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.treewalk.TreeWalk;
import org.spearce.jgit.treewalk.filter.TreeFilter;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

/**
 * The test for submodule utilities
 */
public class SubmoduleTest {
  /**
   * Test loading mapping for submodules
   *
   * @throws IOException if there is IO problem
   */
  @Test
  public void testSubmoduleMapping() throws Exception {
    File masterRep = dataFile("repo.git");
    Repository r = new Repository(masterRep);
    try {
      SubmodulesConfig s = new SubmodulesConfig(r.getConfig(), new BlobBasedConfig(null, r.mapCommit(
        GitUtils.versionRevision(GitVcsSupportTest.SUBMODULE_ADDED_VERSION)), ".gitmodules"));
      assertTrue(s.isSubmodulePrefix(""));
      assertFalse(s.isSubmodulePrefix("submodule"));
      Submodule m = s.findEntry("submodule");
      assertEquals(m.getName(), "submodule");
      assertEquals(m.getPath(), "submodule");
      assertEquals(m.getUrl(), "../submodule.git");
    } finally {
      r.close();
    }
  }

  /**
   * Test loading mapping for submodules
   *
   * @throws IOException if there is IO problem
   */
  @Test
  public void testSubmoduleMultiEntryMapping() throws Exception {
    File masterRep = dataFile("repo.git");
    File submodulesFile = dataFile("content", "dotgitmodules");
    Repository r = new Repository(masterRep);
    try {
      FileBasedConfig config = new FileBasedConfig(null, submodulesFile);
      config.load();
      SubmodulesConfig s = new SubmodulesConfig(r.getConfig(), config);
      assertTrue(s.isSubmodulePrefix(""));
      assertFalse(s.isSubmodulePrefix("c/"));
      Submodule m = s.findEntry("b");
      assertEquals(m.getName(), "b");
      assertEquals(m.getPath(), "b");
      assertEquals(m.getUrl(), "git@gitrep:/git/b.git");
      m = s.findEntry("c/D");
      assertEquals(m.getName(), "c/D");
      assertEquals(m.getPath(), "c/D");
      assertEquals(m.getUrl(), "git@gitrep:/git/d.git");
    } finally {
      r.close();
    }
  }

  /**
   * Test tree walk over submodules
   *
   * @throws IOException in case of test failure
   */
  @Test
  public void testSubmoduleTreeWalk() throws IOException {
    File masterRep = dataFile("repo.git");
    Repository rm = new Repository(masterRep);
    try {
      File submoduleRep = dataFile("submodule.git");
      final Repository rs = new Repository(submoduleRep);
      try {
        final Commit submoduleTxtAdded = rm.mapCommit(
          GitUtils.versionRevision(GitVcsSupportTest.SUBMODULE_TXT_ADDED_VERSION));
        final Commit submoduleModified = rm.mapCommit(
          GitUtils.versionRevision(GitVcsSupportTest.SUBMODULE_MODIFIED_VERSION));
        final Commit submoduleAdded = rm.mapCommit(
          GitUtils.versionRevision(GitVcsSupportTest.SUBMODULE_ADDED_VERSION));
        final Commit beforeSubmoduleAdded = rm.mapCommit(
          GitUtils.versionRevision(GitVcsSupportTest.BEFORE_SUBMODULE_ADDED_VERSION));
        SubmoduleResolver r = new MySubmoduleResolver(submoduleAdded, rs);
        TreeWalk tw = new TreeWalk(rm);
        tw.setRecursive(true);
        tw.reset();
        tw.addTree(SubmoduleAwareTreeIterator.create(beforeSubmoduleAdded, r));
        tw.addTree(SubmoduleAwareTreeIterator.create(submoduleAdded, r));
        tw.setFilter(TreeFilter.ANY_DIFF);
        checkElement(tw, ".gitmodules");
        assertSame(tw.getTree(1, SubmoduleAwareTreeIterator.class).getRepository(), rm);
        checkElement(tw, "submodule/file.txt");
        assertSame(tw.getTree(1, SubmoduleAwareTreeIterator.class).getRepository(), rs);
        assertFalse(tw.next());
        tw.reset();
        tw.addTree(SubmoduleAwareTreeIterator.create(submoduleModified, r));
        tw.addTree(SubmoduleAwareTreeIterator.create(submoduleTxtAdded, r));
        tw.setFilter(TreeFilter.ANY_DIFF);
        checkElement(tw, "submodule.txt");
        assertSame(tw.getTree(1, SubmoduleAwareTreeIterator.class).getRepository(), rm);
        checkElement(tw, "submodule/new file.txt");
        assertSame(tw.getTree(0, SubmoduleAwareTreeIterator.class).getRepository(), rs);
        assertFalse(tw.next());
      } finally {
        rs.close();
      }
    } finally {
      rm.close();
    }
  }

  /**
   * Check element in TreeWalk
   *
   * @param tw   the tree walk
   * @param path the path that should be in the tree walk
   * @throws IOException in case of IO problem
   */
  private void checkElement(TreeWalk tw, final String path) throws IOException {
    assertTrue(tw.next());
    assertEquals(tw.getPathString(), path);
  }

  /**
   * Test byte ranges
   */
  @Test
  public void testByteRanges() {
    ByteRange r = new ByteRange(new byte[0], 0, 0);
    assertEquals(r.hashCode(), 0);
    assertEquals(r, r);
    byte[] b3 = new byte[]{1, 2, 3};
    ByteRange r1 = new ByteRange(b3, 0, 3);
    ByteRange r2 = new ByteRange(b3, 0, 3);
    assertFalse(r1.hashCode() == 0);
    assertEquals(r1, r2);
    assertFalse(r1.equals(r));
  }

  /**
   * Submodule resolver used in the tests
   */
  private static class MySubmoduleResolver extends SubmoduleResolver {
    /**
     * The referenced repository
     */
    private final Repository myReferencedRepository;

    /**
     * The constructor
     *
     * @param baseCommit           the base commit for this resolver. It is used to locate .gitmodules
     * @param referencedRepository the repository to which all URLs are resolved
     */
    public MySubmoduleResolver(Commit baseCommit, Repository referencedRepository) {
      super(baseCommit);
      this.myReferencedRepository = referencedRepository;
    }

    /**
     * {@inheritDoc}
     */
    protected Repository resolveRepository(String path, String url) {
      return myReferencedRepository;
    }

    /**
     * {@inheritDoc}
     */
    public SubmoduleResolver getSubResolver(Commit commit, String path) {
      return new SubmoduleResolver(commit) {
        protected Repository resolveRepository(String path, String url) throws IOException {
          throw new IOException("Repository not found");
        }

        public SubmoduleResolver getSubResolver(Commit commit, String path) {
          throw new RuntimeException("There are no submodules");
        }
      };
    }
  }
}
