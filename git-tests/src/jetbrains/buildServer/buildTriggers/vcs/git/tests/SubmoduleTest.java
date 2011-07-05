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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleAwareTreeIteratorFactory.create;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static org.testng.Assert.*;

/**
 * The test for submodule utilities
 */
public class SubmoduleTest {

  private static TempFiles myTempFiles = new TempFiles();
  private ServerPaths myServerPaths;
  private GitVcsSupport myGitSupport;

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      public void run() {
        myTempFiles.cleanup();
      }
    }));
  }

  @BeforeMethod
  public void setUp() throws IOException {
    File teamcitySystemDir = myTempFiles.createTempDir();
    myServerPaths = new ServerPaths(teamcitySystemDir.getAbsolutePath(), teamcitySystemDir.getAbsolutePath(), teamcitySystemDir.getAbsolutePath());
    final PluginConfigImpl config = new PluginConfigImpl(myServerPaths);
    TransportFactory transportFactory = new TransportFactoryImpl(config, null);
    FetchCommand fetchCommand = new FetchCommandImpl(config, transportFactory);
    myGitSupport = new GitVcsSupport(config, transportFactory, fetchCommand, null);
  }

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }


  /**
   * Test loading mapping for submodules
   *
   * @throws IOException if there is IO problem
   */
  @Test
  public void testSubmoduleMapping() throws Exception {
    File masterRep = dataFile("repo.git");
    Repository r = new RepositoryBuilder().setGitDir(masterRep).build();
    try {
      SubmodulesConfig s = new SubmodulesConfig(r.getConfig(), new BlobBasedConfig(null, r, r.resolve(
        GitUtils.versionRevision(GitVcsSupportTest.SUBMODULE_ADDED_VERSION)), ".gitmodules"));
      assertTrue(s.isSubmodulePrefix(""));
      assertFalse(s.isSubmodulePrefix("submodule"));
      Submodule m = s.findSubmodule("submodule");
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
    Repository r = new RepositoryBuilder().setGitDir(masterRep).build();
    try {
      FileBasedConfig config = new FileBasedConfig(null, submodulesFile, FS.DETECTED);
      config.load();
      SubmodulesConfig s = new SubmodulesConfig(r.getConfig(), config);
      assertTrue(s.isSubmodulePrefix(""));
      assertFalse(s.isSubmodulePrefix("c/"));
      Submodule m = s.findSubmodule("b");
      assertEquals(m.getName(), "b");
      assertEquals(m.getPath(), "b");
      assertEquals(m.getUrl(), "git@gitrep:/git/b.git");
      m = s.findSubmodule("c/D");
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
    Repository rm = new RepositoryBuilder().setGitDir(masterRep).build();
    try {
      File submoduleRep = dataFile("submodule.git");
      final Repository rs = new RepositoryBuilder().setGitDir(submoduleRep).build();
      RevWalk revWalk = new RevWalk(rm);
      try {
        final RevCommit submoduleTxtAdded = revWalk.parseCommit(
          ObjectId.fromString(GitUtils.versionRevision(GitVcsSupportTest.SUBMODULE_TXT_ADDED_VERSION)));
        final RevCommit submoduleModified = revWalk.parseCommit(
          ObjectId.fromString(GitUtils.versionRevision(GitVcsSupportTest.SUBMODULE_MODIFIED_VERSION)));
        final RevCommit submoduleAdded = revWalk.parseCommit(
          ObjectId.fromString(GitUtils.versionRevision(GitVcsSupportTest.SUBMODULE_ADDED_VERSION)));
        final RevCommit beforeSubmoduleAdded = revWalk.parseCommit(
          ObjectId.fromString(GitUtils.versionRevision(GitVcsSupportTest.BEFORE_SUBMODULE_ADDED_VERSION)));
        SubmoduleResolver r = new MySubmoduleResolver(myGitSupport, rm, submoduleAdded, rs);
        TreeWalk tw = new TreeWalk(rm);
        tw.setRecursive(true);
        tw.reset();
        tw.addTree(create(rm, beforeSubmoduleAdded, r, "", "", SubmodulesCheckoutPolicy.CHECKOUT, true));
        tw.addTree(create(rm, submoduleAdded, r, "", "", SubmodulesCheckoutPolicy.CHECKOUT, true));
        tw.setFilter(TreeFilter.ANY_DIFF);
        checkElement(tw, ".gitmodules");
        assertSame(tw.getTree(1, SubmoduleAwareTreeIterator.class).getRepository(), rm);
        checkElement(tw, "submodule/file.txt");
        assertSame(tw.getTree(1, SubmoduleAwareTreeIterator.class).getRepository(), rs);
        assertFalse(tw.next());
        tw.reset();
        tw.addTree(create(rm, submoduleModified, r, "", "", SubmodulesCheckoutPolicy.CHECKOUT, true));
        tw.addTree(create(rm, submoduleTxtAdded, r, "", "", SubmodulesCheckoutPolicy.CHECKOUT, true));
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
    private final GitVcsSupport myGitSupport;
    private final Repository myDb;

    public MySubmoduleResolver(GitVcsSupport gitSupport, Repository db, RevCommit commit, Repository referencedRepository) {
      super(gitSupport, db, commit);
      this.myReferencedRepository = referencedRepository;
      myGitSupport = gitSupport;
      myDb = db;
    }

    /**
     * {@inheritDoc}
     */
    protected Repository resolveRepository(String path, String url) {
      return myReferencedRepository;
    }

    @Override
    public SubmoduleResolver getSubResolver(RevCommit commit, String path) {
      return new SubmoduleResolver(myGitSupport, myReferencedRepository, commit) {
        protected Repository resolveRepository(String path, String url) throws IOException {
          throw new IOException("Repository not found");
        }
        public SubmoduleResolver getSubResolver(RevCommit commit, String path) {
          throw new RuntimeException("There are no submodules");
        }
      };
    }
  }
}
