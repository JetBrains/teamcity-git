package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spearce.jgit.lib.*;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevSort;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.transport.*;
import org.spearce.jgit.treewalk.EmptyTreeIterator;
import org.spearce.jgit.treewalk.TreeWalk;
import org.spearce.jgit.treewalk.filter.TreeFilter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;


/**
 * Git VCS support
 */
public class GitVcsSupport extends VcsSupport implements LabelingSupport {
  /**
   * Random number generator used to generate artifitial versions
   */
  private static final Random ourRandom = new Random();
  /**
   * Paths to the server
   */
  final ServerPaths myServerPaths;

  /**
   * The constructor
   *
   * @param serverPaths the paths to the server
   */
  public GitVcsSupport(ServerPaths serverPaths) {
    this.myServerPaths = serverPaths;
  }

  /**
   * {@inheritDoc}
   */
  public List<ModificationData> collectBuildChanges(VcsRoot root,
                                                    @NotNull String fromVersion,
                                                    @NotNull String currentVersion,
                                                    CheckoutRules checkoutRules) throws VcsException {
    List<ModificationData> rc = new ArrayList<ModificationData>();
    Settings s = createSettings(root);
    try {
      Repository r = GitUtils.getRepository(s.getRepositoryPath(), s.getRepositoryURL());
      try {
        final String current = GitUtils.versionRevision(currentVersion);
        final String from = GitUtils.versionRevision(fromVersion);
        RevWalk revs = new RevWalk(r);
        final RevCommit currentRev = revs.parseCommit(ObjectId.fromString(current));
        revs.markStart(currentRev);
        revs.sort(RevSort.TOPO);
        revs.sort(RevSort.COMMIT_TIME_DESC);
        final ObjectId fromId = ObjectId.fromString(from);
        if (r.hasObject(fromId)) {
          final RevCommit fromRev = revs.parseCommit(fromId);
          revs.markUninteresting(fromRev);
          RevCommit c;
          while ((c = revs.next()) != null) {
            addCommit(root, rc, r, revs, c);
          }
        } else {
          RevCommit c;
          long limitTime = GitUtils.versionTime(fromVersion);
          while ((c = revs.next()) != null) {
            if (c.getCommitTime() * 1000L <= limitTime) {
              revs.markUninteresting(c);
            } else {
              addCommit(root, rc, r, revs, c);
            }
          }
          // add revision with warning text and randmon number as version
          byte[] idBytes = new byte[20];
          ourRandom.nextBytes(idBytes);
          String version = GitUtils.makeVersion(ObjectId.fromRaw(idBytes).name(), limitTime);
          rc.add(new ModificationData(new Date(currentRev.getCommitTime()),
                                      new ArrayList<VcsChange>(),
                                      "The previous version was removed from repository, " +
                                      "getting changes using date. The changes reported might be not accurate.",
                                      GitUtils.SYSTEM_USER,
                                      root,
                                      version,
                                      GitUtils.displayVersion(version)));
        }
      } finally {
        r.close();
      }
    } catch (VcsException e) {
      throw e;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new VcsException("The get content failed: " + e, e);
    }
    // TODO checkout rules are ignored right now
    return rc;
  }

  /**
   * Add commit as a list of modification data
   *
   * @param root the vcs root
   * @param rc   list of commits to update
   * @param r    repository
   * @param revs revision iterator
   * @param c    the current commit
   * @throws IOException in case of IO problem
   */
  private static void addCommit(VcsRoot root, List<ModificationData> rc, Repository r, RevWalk revs, RevCommit c) throws IOException {
    Commit cc = c.asCommit(revs);
    final ObjectId[] parentIds = cc.getParentIds();
    String cv = GitUtils.makeVersion(cc);
    String pv =
      parentIds.length == 0 ? GitUtils.makeVersion(ObjectId.zeroId().name(), 0) : GitUtils.makeVersion(r.mapCommit(cc.getParentIds()[0]));
    List<VcsChange> changes = getCommitChanges(r, cc, parentIds, cv, pv);
    ModificationData m = new ModificationData(cc.getCommitter().getWhen(), changes, cc.getMessage(), GitUtils.getUser(cc), root, cv,
                                              GitUtils.displayVersion(cc));
    rc.add(m);
  }

  /**
   * Get changes for the commit
   *
   * @param r         the change version
   * @param cc        the current commit
   * @param parentIds parent commit identifiers
   * @param cv        the commit version
   * @param pv        the parent commit version
   * @return the commit changes
   * @throws IOException if there is a repository access problem
   */
  private static List<VcsChange> getCommitChanges(Repository r, Commit cc, ObjectId[] parentIds, String cv, String pv) throws IOException {
    List<VcsChange> changes = new ArrayList<VcsChange>();
    TreeWalk tw = new TreeWalk(r);
    tw.setFilter(TreeFilter.ANY_DIFF);
    tw.setRecursive(true);
    // remove empty tree iterator before adding new tree
    tw.reset();
    tw.addTree(cc.getTreeId());
    int nTrees = parentIds.length + 1;
    for (ObjectId pid : parentIds) {
      Commit pc = r.mapCommit(pid);
      tw.addTree(pc.getTreeId());
    }
    while (tw.next()) {
      String path = tw.getPathString();
      VcsChange.Type type;
      String description = null;
      switch (classifyChange(nTrees, tw)) {
        case UNCHANGED:
          // change is ignored
          continue;
        case ADDED:
          type = VcsChange.Type.ADDED;
          break;
        case DELETED:
          type = VcsChange.Type.REMOVED;
          break;
        case MODIFIED:
          type = VcsChange.Type.CHANGED;
          break;
        case FILE_MODE_CHANGED:
          type = VcsChange.Type.CHANGED;
          description = "File mode changed";
          break;
        default:
          throw new IllegalStateException("Unknown change type");
      }
      VcsChange change = new VcsChange(type, description, path, path, pv, cv);
      changes.add(change);
    }
    return changes;
  }

  /**
   * Classify change in tree walker. The first tree is assumed to be a current commit and other
   * trees are assumed to be parent commits. In the case of multiple changes, the changes that
   * come from at lease one parent commit are assumed to be reported in the parent commit.
   *
   * @param nTrees number of trees
   * @param tw     tree walker to examine
   * @return change type
   */
  @NotNull
  private static ChangeType classifyChange(int nTrees, @NotNull TreeWalk tw) {
    final FileMode mode0 = tw.getFileMode(0);
    if (FileMode.MISSING.equals(mode0)) {
      for (int i = 1; i < nTrees; i++) {
        if (FileMode.MISSING.equals(tw.getFileMode(i))) {
          // the delete merge
          return ChangeType.UNCHANGED;
        }
      }
      return ChangeType.DELETED;
    }
    boolean fileAdded = true;
    for (int i = 1; i < nTrees; i++) {
      if (!FileMode.MISSING.equals(tw.getFileMode(i))) {
        fileAdded = false;
        break;
      }
    }
    if (fileAdded) {
      return ChangeType.ADDED;
    }
    boolean fileModified = true;
    for (int i = 1; i < nTrees; i++) {
      if (tw.idEqual(0, i)) {
        fileModified = false;
        break;
      }
    }
    if (fileModified) {
      return ChangeType.MODIFIED;
    }
    int modeBits0 = mode0.getBits();
    boolean fileModeModified = true;
    for (int i = 1; i < nTrees; i++) {
      int modebits = tw.getFileMode(i).getBits();
      if (modebits == modeBits0) {
        fileModeModified = false;
        break;
      }
    }
    if (fileModeModified) {
      return ChangeType.FILE_MODE_CHANGED;
    }
    return ChangeType.UNCHANGED;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isTestConnectionSupported() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public void buildPatch(@NotNull VcsRoot root,
                         @Nullable String fromVersion,
                         @NotNull String toVersion,
                         @NotNull PatchBuilder builder,
                         @NotNull CheckoutRules checkoutRules) throws IOException, VcsException {
    // TODO checkout rules are ignored right now
    Settings s = createSettings(root);
    try {
      Repository r = GitUtils.getRepository(s.getRepositoryPath(), s.getRepositoryURL());
      try {
        TreeWalk tw = new TreeWalk(r);
        tw.setFilter(TreeFilter.ANY_DIFF);
        tw.setRecursive(true);
        tw.reset();
        Commit toCommit = r.mapCommit(GitUtils.versionRevision(toVersion));
        if (toCommit == null) {
          throw new VcsException("Missing commit for version: " + toVersion);
        }
        tw.addTree(toCommit.getTreeId());
        if (fromVersion != null) {
          Commit fromCommit = r.mapCommit(GitUtils.versionRevision(fromVersion));
          if (fromCommit == null) {
            // TODO add handling for the missing from version
            throw new RuntimeException();
          }
          tw.addTree(fromCommit.getTreeId());
        } else {
          tw.addTree(new EmptyTreeIterator());
        }
        while (tw.next()) {
          String path = tw.getPathString();
          switch (classifyChange(2, tw)) {
            case UNCHANGED:
              // change is ignored
              continue;
            case MODIFIED:
            case ADDED:
            case FILE_MODE_CHANGED:
              ObjectId blobId = tw.getObjectId(0);
              ObjectLoader loader = r.openBlob(blobId);
              byte[] bytes = loader.getCachedBytes();
              String mode = getModeDiff(tw);
              builder.changeOrCreateBinaryFile(GitUtils.toFile(path), mode, new ByteArrayInputStream(bytes), bytes.length);
              break;
            case DELETED:
              builder.deleteFile(GitUtils.toFile(path), true);
              break;
            default:
              throw new IllegalStateException("Unknown change type");
          }
        }
      } finally {
        r.close();
      }
    } catch (VcsException e) {
      throw e;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new VcsException("The get content failed: " + e, e);
    }
  }

  /**
   * Get difference in the file mode (passed to chmod), null if there is no difference
   *
   * @param tw the tree walker to check
   * @return the mode difference or null if there is no different
   */
  private static String getModeDiff(TreeWalk tw) {
    boolean cExec = isExecutable(tw.getFileMode(0));
    boolean pExec = isExecutable(tw.getFileMode(1));
    String mode;
    if (cExec & !pExec) {
      mode = "a+x";
    } else if (!cExec & pExec) {
      mode = "a-x";
    } else {
      mode = null;
    }
    return mode;
  }

  /**
   * Check if the file mode is executable
   *
   * @param m file mode to check
   * @return true if the file is executable
   */
  private static boolean isExecutable(FileMode m) {
    return (m.getBits() & (1 << 6)) != 0;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public byte[] getContent(VcsModification vcsModification, VcsChangeInfo change, VcsChangeInfo.ContentType contentType, VcsRoot vcsRoot)
    throws VcsException {
    String version = contentType == VcsChangeInfo.ContentType.BEFORE_CHANGE
                     ? change.getBeforeChangeRevisionNumber()
                     : change.getAfterChangeRevisionNumber();
    String file = change.getRelativeFileName();
    return getContent(file, vcsRoot, version);
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public byte[] getContent(String filePath, VcsRoot versionedRoot, String version) throws VcsException {
    Settings s = createSettings(versionedRoot);
    try {
      Repository r = GitUtils.getRepository(s.getRepositoryPath(), s.getRepositoryURL());
      try {
        final String rev = GitUtils.versionRevision(version);
        Commit c = r.mapCommit(rev);
        if (c == null) {
          throw new VcsException("The version name could not be resolved " + rev);
        }
        Tree t = c.getTree();
        TreeEntry e = t.findBlobMember(filePath);
        ObjectId id = e.getId();
        final ObjectLoader loader = r.openBlob(id);
        return loader.getBytes();
      } finally {
        r.close();
      }
    } catch (VcsException e) {
      throw e;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new VcsException("The get content failed: " + e, e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public String getName() {
    return Constants.VCS_NAME;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public String getDisplayName() {
    return "Git";
  }

  /**
   * {@inheritDoc}
   */
  public PropertiesProcessor getVcsPropertiesProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> properties) {
        Collection<InvalidProperty> rc = new LinkedList<InvalidProperty>();
        String url = properties.get(Constants.URL);
        if (url == null || url.trim().length() == 0) {
          rc.add(new InvalidProperty(Constants.URL, "The URL must be specified"));
        } else {
          try {
            new URIish(url);
          } catch (URISyntaxException e) {
            rc.add(new InvalidProperty(Constants.URL, "Invalid URL syntax: " + url));
          }
        }
        return rc;
      }
    };
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public String getVcsSettingsJspFilePath() {
    return "gitSettings.jsp";
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public String describeVcsRoot(VcsRoot root) {
    return root.getProperty(Constants.URL) + "#" + root.getProperty(Constants.BRANCH_NAME);
  }

  /**
   * {@inheritDoc}
   */
  public Map<String, String> getDefaultVcsProperties() {
    final HashMap<String, String> map = new HashMap<String, String>();
    map.put(Constants.BRANCH_NAME, "master");
    return map;
  }

  /**
   * {@inheritDoc}
   */
  public String getVersionDisplayName(@NotNull String version, @NotNull VcsRoot root) throws VcsException {
    return GitUtils.displayVersion(version);
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public Comparator<String> getVersionComparator() {
    return GitUtils.VERSION_COMPATOR;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public String getCurrentVersion(@NotNull VcsRoot root) throws VcsException {
    Settings s = createSettings(root);
    try {
      Repository r = GitUtils.getRepository(s.getRepositoryPath(), s.getRepositoryURL());
      try {
        String refName = GitUtils.branchRef(s.getBranch());
        // Fetch current version of the branch
        final Transport tn = Transport.open(r, s.getRepositoryURL());
        try {
          RefSpec spec = new RefSpec().setSource(refName).setDestination(refName).setForceUpdate(true);
          tn.fetch(NullProgressMonitor.INSTANCE, Collections.singletonList(spec));
        } finally {
          tn.close();
        }
        Commit c = r.mapCommit(refName);
        if (c == null) {
          throw new VcsException("The branch name could not be resolved " + refName);
        }
        return GitUtils.makeVersion(c);
      } finally {
        r.close();
      }
    } catch (VcsException e) {
      throw e;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new VcsException("The current version failed: " + e, e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public String testConnection(@NotNull VcsRoot vcsRoot) throws VcsException {
    Settings s = createSettings(vcsRoot);
    try {
      Repository r = GitUtils.getRepository(s.getRepositoryPath(), s.getRepositoryURL());
      try {
        final Transport tn = Transport.open(r, s.getRepositoryURL());
        try {
          final FetchConnection c = tn.openFetch();
          try {
            String refName = GitUtils.branchRef(s.getBranch());
            for (final Ref ref : c.getRefs()) {
              if (refName.equals(ref.getName())) {
                return null;
              }
            }
            throw new VcsException("The branch " + refName + " was not found in the repository " + s.getRepositoryURL());
          } finally {
            c.close();
          }
        } finally {
          tn.close();
        }
      } finally {
        r.close();
      }
    } catch (VcsException e) {
      throw e;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new VcsException("Repository test failed: " + e, e);
    }
  }

  /**
   * Create settings object
   *
   * @param vcsRoot the root object
   * @return the created object
   */
  private Settings createSettings(VcsRoot vcsRoot) throws VcsException {
    final Settings settings = new Settings(vcsRoot);
    if (settings.getRepositoryPath() == null) {
      String url = settings.getRepositoryURL();
      File dir = new File(myServerPaths.getCachesDir());
      String name = String.format("git-%08X.git", url.hashCode() & 0xFFFFFFFFL);
      settings.setRepositoryPath(new File(dir, "git" + File.separatorChar + name));
    }
    return settings;
  }

  /**
   * {@inheritDoc}
   */
  public LabelingSupport getLabelingSupport() {
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public String label(@NotNull String label, @NotNull String version, @NotNull VcsRoot root, @NotNull CheckoutRules checkoutRules)
    throws VcsException {
    Settings s = createSettings(root);
    try {
      Repository r = GitUtils.getRepository(s.getRepositoryPath(), s.getRepositoryURL());
      try {
        Tag t = new Tag(r);
        t.setTag(label);
        t.setObjId(versionObjectId(version));
        t.tag();
        String tagRef = GitUtils.tagName(label);
        final Transport tn = Transport.open(r, s.getRepositoryURL());
        try {
          final PushConnection c = tn.openPush();
          try {
            RemoteRefUpdate ru = new RemoteRefUpdate(r, tagRef, tagRef, false, null, null);
            c.push(NullProgressMonitor.INSTANCE, Collections.singletonMap(tagRef, ru));
            switch (ru.getStatus()) {
              case UP_TO_DATE:
              case OK:
                break;
              default:
                throw new VcsException("The remote reference was not updated: " + label);
            }
          } finally {
            c.close();
          }
          return label;
        } finally {
          tn.close();
        }
      } finally {
        r.close();
      }
    } catch (VcsException e) {
      throw e;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new VcsException("Repository test failed: " + e, e);
    }
  }

  /**
   * Make object identifier from the version string
   *
   * @param version the version string
   * @return object identifier
   */
  private static ObjectId versionObjectId(String version) {
    return ObjectId.fromString(GitUtils.versionRevision(version));
  }

  /**
   * Git change type
   */
  private enum ChangeType {
    /**
     * the file is added
     */
    ADDED,
    /**
     * the file is deleted
     */
    DELETED,
    /**
     * the file content (or content+mode) changed
     */
    MODIFIED,
    /**
     * the file mode only changed
     */
    FILE_MODE_CHANGED,
    /**
     * no change detected
     */
    UNCHANGED,
  }
}
