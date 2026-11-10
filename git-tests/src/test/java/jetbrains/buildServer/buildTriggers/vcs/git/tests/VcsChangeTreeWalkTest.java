package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.VcsChangeTreeWalk;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class VcsChangeTreeWalkTest extends BaseTestCase {

  private Repository myRepository;

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRepository = Git.init().setBare(true).setDirectory(createTempDir()).call().getRepository();
  }

  @AfterMethod(alwaysRun = true)
  @Override
  protected void tearDown() throws Exception {
    if (myRepository != null) myRepository.close();
    super.tearDown();
  }


  public void no_common_files_when_unrelated_history_is_merged_under_a_prefix() throws IOException {
    ObjectId commit = tree(dir("projectA", file("a.txt", "a")),
                           dir("projectB", file("README", "readme"), dir("src", file("b.txt", "b"))));
    ObjectId parent = tree(file("README", "readme"), dir("src", file("b.txt", "b")));

    then(hasAnyCommonFiles(commit, parent)).isFalse();
  }


  public void no_common_files_when_directories_have_the_same_names_but_different_files() throws IOException {
    ObjectId commit = tree(dir("src", dir("main", file("A.java", "a1"), file("B.java", "b1"))));
    ObjectId parent = tree(dir("src", dir("main", file("A.java", "a2"), file("C.java", "c"))));

    then(hasAnyCommonFiles(commit, parent)).isFalse();
  }


  public void common_file_when_only_non_canonical_file_mode_differs() throws IOException {
    // the per-parent changed files compare canonical modes, 100664 and 100644 are both a regular file there
    ObjectId commit = tree(file("a.txt", 0100664, "a"), file("b.txt", "b1"));
    ObjectId parent = tree(file("a.txt", "a"), file("b.txt", "b2"));

    then(hasAnyCommonFiles(commit, parent)).isTrue();
  }


  public void no_common_files_when_content_is_the_same_but_file_mode_differs() throws IOException {
    ObjectId commit = tree(executable("run.sh", "echo"));
    ObjectId parent = tree(file("run.sh", "echo"));

    then(hasAnyCommonFiles(commit, parent)).isFalse();
  }


  public void no_common_files_when_file_replaced_directory() throws IOException {
    ObjectId commit = tree(file("lib", "lib"));
    ObjectId parent = tree(dir("lib", file("lib", "lib")));

    then(hasAnyCommonFiles(commit, parent)).isFalse();
  }


  public void no_common_files_in_empty_commit_tree() throws IOException {
    ObjectId commit = tree();
    ObjectId parent = tree(file("README", "readme"));

    then(hasAnyCommonFiles(commit, parent)).isFalse();
  }


  public void common_files_when_submodule_changed_comparing_to_parent() throws IOException {
    // submodule checkout compares the submodule files, e.g. an unchanged sub/selected/file, which gitlinks don't show
    ObjectId commit = tree(file(".gitmodules", "sub v2"), submodule("sub", "revision 2"));
    ObjectId parent = tree(file(".gitmodules", "sub v1"), submodule("sub", "revision 1"));

    then(hasAnyCommonFiles(commit, parent)).isTrue();
  }


  public void common_files_when_submodule_replaced_directory() throws IOException {
    ObjectId commit = tree(submodule("sub", "revision 1"));
    ObjectId parent = tree(dir("sub", file("file", "content")));

    then(hasAnyCommonFiles(commit, parent)).isTrue();
  }


  public void common_files_when_directory_replaced_submodule() throws IOException {
    ObjectId commit = tree(dir("sub", file("file", "content")));
    ObjectId parent = tree(submodule("sub", "revision 1"));

    then(hasAnyCommonFiles(commit, parent)).isTrue();
  }


  public void no_common_files_when_submodule_exists_in_commit_only() throws IOException {
    ObjectId commit = tree(file("a.txt", "a"), submodule("sub", "revision 1"));
    ObjectId parent = tree(file("b.txt", "b"));

    then(hasAnyCommonFiles(commit, parent)).isFalse();
  }


  public void no_common_files_when_submodule_exists_in_parent_only() throws IOException {
    ObjectId commit = tree(file("a.txt", "a1"));
    ObjectId parent = tree(file("a.txt", "a2"), submodule("sub", "revision 1"));

    then(hasAnyCommonFiles(commit, parent)).isFalse();
  }


  public void common_files_when_both_trees_are_empty() throws IOException {
    ObjectId commit = tree();
    ObjectId parent = tree();

    then(hasAnyCommonFiles(commit, parent)).isTrue();
  }


  public void common_file_at_root() throws IOException {
    ObjectId commit = tree(file(".gitignore", "target"), file("a.txt", "a1"));
    ObjectId parent = tree(file(".gitignore", "target"), file("a.txt", "a2"));

    then(hasAnyCommonFiles(commit, parent)).isTrue();
  }


  public void common_file_in_changed_directory() throws IOException {
    ObjectId commit = tree(dir("src", dir("main", file("A.java", "a1"), file("B.java", "b"))));
    ObjectId parent = tree(dir("src", dir("main", file("A.java", "a2"), file("B.java", "b"))));

    then(hasAnyCommonFiles(commit, parent)).isTrue();
  }


  public void common_directory() throws IOException {
    ObjectId commit = tree(dir("docs", file("index.md", "docs")), file("a.txt", "a1"));
    ObjectId parent = tree(dir("docs", file("index.md", "docs")), file("a.txt", "a2"));

    then(hasAnyCommonFiles(commit, parent)).isTrue();
  }


  private boolean hasAnyCommonFiles(@NotNull ObjectId commitTreeId, @NotNull ObjectId parentTreeId) throws IOException {
    ObjectId parentId = commit(parentTreeId);
    ObjectId commitId = commit(commitTreeId, parentId);
    try (ObjectReader reader = myRepository.newObjectReader();
         RevWalk revWalk = new RevWalk(reader);
         VcsChangeTreeWalk tw = new VcsChangeTreeWalk(reader, "test repository", false)) {
      RevCommit commit = revWalk.parseCommit(commitId);
      RevCommit parentCommit = revWalk.parseCommit(parentId);
      return tw.hasAnyCommonFiles(commit, parentCommit);
    }
  }


  @NotNull
  private ObjectId commit(@NotNull ObjectId treeId, @NotNull ObjectId... parentIds) throws IOException {
    PersonIdent ident = new PersonIdent("user", "user@example.com");
    CommitBuilder commit = new CommitBuilder();
    commit.setTreeId(treeId);
    commit.setParentIds(parentIds);
    commit.setAuthor(ident);
    commit.setCommitter(ident);
    commit.setMessage("commit");
    try (ObjectInserter inserter = myRepository.newObjectInserter()) {
      ObjectId id = inserter.insert(commit);
      inserter.flush();
      return id;
    }
  }


  @NotNull
  private ObjectId tree(@NotNull Entry... entries) throws IOException {
    List<Entry> sorted = new ArrayList<>(Arrays.asList(entries));
    // git orders tree entries by name, with directory names compared as if they ended with '/'
    sorted.sort(Comparator.comparing(e -> FileMode.TREE.equals(e.rawMode) ? e.name + "/" : e.name));
    // written directly: TreeFormatter takes a FileMode, which cannot represent non-canonical modes such as 100664
    ByteArrayOutputStream content = new ByteArrayOutputStream();
    for (Entry e : sorted) {
      content.write((Integer.toOctalString(e.rawMode) + " " + e.name).getBytes(StandardCharsets.UTF_8));
      content.write(0);
      byte[] id = new byte[Constants.OBJECT_ID_LENGTH];
      e.id.copyRawTo(id, 0);
      content.write(id);
    }
    try (ObjectInserter inserter = myRepository.newObjectInserter()) {
      ObjectId id = inserter.insert(Constants.OBJ_TREE, content.toByteArray());
      inserter.flush();
      return id;
    }
  }

  @NotNull
  private Entry dir(@NotNull String name, @NotNull Entry... entries) throws IOException {
    return new Entry(name, FileMode.TREE.getBits(), tree(entries));
  }

  @NotNull
  private Entry file(@NotNull String name, @NotNull String content) throws IOException {
    return new Entry(name, FileMode.REGULAR_FILE.getBits(), blob(content));
  }

  /**
   * A regular file with a non-canonical mode, e.g. 0100664 written by old git versions.
   */
  @NotNull
  private Entry file(@NotNull String name, int rawMode, @NotNull String content) throws IOException {
    return new Entry(name, rawMode, blob(content));
  }

  @NotNull
  private Entry executable(@NotNull String name, @NotNull String content) throws IOException {
    return new Entry(name, FileMode.EXECUTABLE_FILE.getBits(), blob(content));
  }

  /**
   * A gitlink to a commit of the submodule, the commit is created in this repository: the check doesn't read it.
   */
  @NotNull
  private Entry submodule(@NotNull String name, @NotNull String revisionContent) throws IOException {
    return new Entry(name, FileMode.GITLINK.getBits(), commit(tree(file("file", revisionContent))));
  }

  @NotNull
  private ObjectId blob(@NotNull String content) throws IOException {
    try (ObjectInserter inserter = myRepository.newObjectInserter()) {
      ObjectId id = inserter.insert(Constants.OBJ_BLOB, content.getBytes(StandardCharsets.UTF_8));
      inserter.flush();
      return id;
    }
  }

  private static final class Entry {
    private final String name;
    private final int rawMode;
    private final ObjectId id;

    private Entry(@NotNull String name, int rawMode, @NotNull ObjectId id) {
      this.name = name;
      this.rawMode = rawMode;
      this.id = id;
    }
  }
}
