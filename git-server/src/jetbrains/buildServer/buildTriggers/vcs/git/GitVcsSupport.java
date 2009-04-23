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

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.ssh.HeadlessSshSessionFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.ssh.PasswordSshSessionFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.ssh.PrivateKeyFileSshSessionFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.ssh.RefreshableSshConfigSessionFactory;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spearce.jgit.errors.NotSupportedException;
import org.spearce.jgit.errors.TransportException;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.*;


/**
 * Git VCS support
 */
public class GitVcsSupport extends ServerVcsSupport
  implements LabelingSupport, VcsFileContentProvider, CollectChangesByCheckoutRules, BuildPatchByCheckoutRules {
  /**
   * logger instance
   */
  private static Logger LOG = Logger.getInstance(GitVcsSupport.class.getName());
  /**
   * Random number generator used to generate artifitial versions
   */
  private static final Random ourRandom = new Random();
  /**
   * Paths to the server
   */
  final ServerPaths myServerPaths;
  /**
   * The default SSH session factory used for not explicitly configured host
   * It fails when user is prompted for some information.
   */
  final SshSessionFactory mySshSessionFactory;

  /**
   * The constructor
   *
   * @param serverPaths the paths to the server
   */
  public GitVcsSupport(@Nullable ServerPaths serverPaths) {
    this.myServerPaths = serverPaths;
    if (serverPaths == null) {
      // the test mode
      this.mySshSessionFactory = new HeadlessSshSessionFactory();
    } else {
      this.mySshSessionFactory = new RefreshableSshConfigSessionFactory();
    }
  }

  /**
   * Convert exception to vcs exception with readable message
   *
   * @param operation operation name (like "collecting changes")
   * @param ex        an exception to convert
   * @return a converted vcs exception
   */
  private static VcsException processException(String operation, Exception ex) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("The error during GIT vcs operation " + operation, ex);
    }
    if (ex instanceof VcsException) {
      return (VcsException)ex;
    }
    if (ex instanceof RuntimeException) {
      throw (RuntimeException)ex;
    }
    String message;
    if (ex instanceof TransportException && ex.getCause() != null) {
      Throwable t = ex.getCause();
      if (t instanceof FileNotFoundException) {
        message = "File not found: " + t.getMessage();
      } else if (t instanceof UnknownHostException) {
        message = "Unknown host: " + t.getMessage();
      } else {
        message = t.toString();
      }
    } else {
      StringBuilder b = new StringBuilder();
      for (Throwable t = ex; t != null; t = t.getCause()) {
        b.append('\n');
        b.append(t.toString());
      }
      message = b.toString();
    }
    return new VcsException("The " + operation + " failed: " + message, ex);
  }


  /**
   * {@inheritDoc}
   */
  public List<ModificationData> collectChanges(@NotNull VcsRoot root,
                                               @NotNull String fromVersion,
                                               @Nullable String currentVersion,
                                               @NotNull CheckoutRules checkoutRules) throws VcsException {
    List<ModificationData> rc = new ArrayList<ModificationData>();
    Settings s = createSettings(root);
    try {
      Repository r = getRepository(s);
      try {
        LOG.info("Collecting changes " + fromVersion + ".." + currentVersion + " for " + s.debugInfo());
        final String current = GitUtils.versionRevision(currentVersion);
        ensureCommitLoaded(s, r, current);
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
            addCommit(root, rc, r, s, revs, c);
          }
        } else {
          LOG.warn("The from version " + fromVersion + " is not found, collecting changes basing on date and commit time " + s.debugInfo());
          RevCommit c;
          long limitTime = GitUtils.versionTime(fromVersion);
          while ((c = revs.next()) != null) {
            if (c.getCommitTime() * 1000L <= limitTime) {
              revs.markUninteresting(c);
            } else {
              addCommit(root, rc, r, s, revs, c);
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
    } catch (Exception e) {
      throw processException("collecting changes", e);
    }
    return rc;
  }

  /**
   * Add commit as a list of modification data
   *
   * @param root the vcs root
   * @param rc   list of commits to update
   * @param r    repository
   * @param s    the settings object
   * @param revs revision iterator
   * @param c    the current commit
   * @throws IOException in case of IO problem
   */
  private static void addCommit(VcsRoot root, List<ModificationData> rc, Repository r, Settings s, RevWalk revs, RevCommit c)
    throws IOException {
    Commit cc = c.asCommit(revs);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "Collecting changes in commit " + c.getId() + ":" + c.getShortMessage() + " (" + c.getCommitterIdent().getWhen() + ") for " +
        s.debugInfo());
    }
    final ObjectId[] parentIds = cc.getParentIds();
    String cv = GitUtils.makeVersion(cc);
    String pv =
      parentIds.length == 0 ? GitUtils.makeVersion(ObjectId.zeroId().name(), 0) : GitUtils.makeVersion(r.mapCommit(cc.getParentIds()[0]));
    List<VcsChange> changes = getCommitChanges(s, r, cc, parentIds, cv, pv);
    ModificationData m = new ModificationData(cc.getCommitter().getWhen(), changes, cc.getMessage(), GitUtils.getUser(s, cc), root, cv,
                                              GitUtils.displayVersion(cc));
    rc.add(m);
  }

  /**
   * Get changes for the commit
   *
   * @param s         the setting object
   * @param r         the change version
   * @param cc        the current commit
   * @param parentIds parent commit identifiers
   * @param cv        the commit version
   * @param pv        the parent commit version
   * @return the commit changes
   * @throws IOException if there is a repository access problem
   */
  private static List<VcsChange> getCommitChanges(Settings s, Repository r, Commit cc, ObjectId[] parentIds, String cv, String pv)
    throws IOException {
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
      final ChangeType changeType = classifyChange(nTrees, tw);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Processing change " + treeWalkInfo(tw) + " as " + changeType + " " + s.debugInfo());
      }
      switch (changeType) {
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
                         @NotNull PatchBuilder builderOrig,
                         @NotNull CheckoutRules checkoutRules) throws IOException, VcsException {
    final boolean debugFlag = LOG.isDebugEnabled();
    final PatchBuilderFileNamesCorrector builder = new PatchBuilderFileNamesCorrector(builderOrig);
    builder.setWorkingMode_WithCheckoutRules(checkoutRules);
    Settings s = createSettings(root);
    try {
      Repository r = getRepository(s);
      try {
        Commit toCommit = ensureCommitLoaded(s, r, GitUtils.versionRevision(toVersion));
        if (toCommit == null) {
          throw new VcsException("Missing commit for version: " + toVersion);
        }
        TreeWalk tw = new TreeWalk(r);
        tw.setFilter(TreeFilter.ANY_DIFF);
        tw.setRecursive(true);
        tw.reset();
        tw.addTree(toCommit.getTreeId());
        if (fromVersion != null) {
          LOG.info("Creating patch " + fromVersion + ".." + toVersion + " for " + s.debugInfo());
          Commit fromCommit = r.mapCommit(GitUtils.versionRevision(fromVersion));
          if (fromCommit == null) {
            throw new IncrementalPatchImpossibleException("The form commit " + fromVersion + " is not availalbe in the repository");
          }
          tw.addTree(fromCommit.getTreeId());
        } else {
          LOG.info("Creating clean patch " + toVersion + " for " + s.debugInfo());
          tw.addTree(new EmptyTreeIterator());
        }
        while (tw.next()) {
          String path = tw.getPathString();
          if (debugFlag) {
            LOG.debug("File found " + treeWalkInfo(tw) + " for " + s.debugInfo());
          }
          final File file = GitUtils.toFile(path);
          switch (classifyChange(2, tw)) {
            case UNCHANGED:
              // change is ignored
              continue;
            case MODIFIED:
            case ADDED:
            case FILE_MODE_CHANGED:
              if (isFileIncluded(checkoutRules, file)) {
                ObjectId blobId = tw.getObjectId(0);
                ObjectLoader loader = r.openBlob(blobId);
                byte[] bytes = loader.getCachedBytes();
                String mode = getModeDiff(tw);
                builder.changeOrCreateBinaryFile(file, mode, new ByteArrayInputStream(bytes), bytes.length);
              }
              break;
            case DELETED:
              builder.deleteFile(file, true);
              break;
            default:
              throw new IllegalStateException("Unknown change type");
          }
        }
      } finally {
        r.close();
      }
    } catch (Exception e) {
      throw processException("patch building", e);
    }
  }

  /**
   * Get debug info for treewalk (used in logging)
   *
   * @param tw tree walk object
   * @return debug info about tree walk
   */
  private static String treeWalkInfo(TreeWalk tw) {
    StringBuilder b = new StringBuilder();
    b.append(tw.getPathString());
    b.append('(');
    final int n = tw.getTreeCount();
    for (int i = 0; i < n; i++) {
      if (i != 0) {
        b.append(", ");
      }
      b.append(tw.getObjectId(i).name());
      b.append(String.format("%04o", tw.getFileMode(i).getBits()));
    }
    b.append(')');
    return b.toString();
  }

  /**
   * Check if the file is included
   *
   * @param checkoutRules checkout rules to check
   * @param path          the path to check
   * @return true if the file is included
   */
  private static boolean isFileIncluded(CheckoutRules checkoutRules, File path) {
    return checkoutRules.getIncludeRuleFor(path.getPath()) != null;
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
    if (mode != null && LOG.isDebugEnabled()) {
      LOG.debug("The mode change " + mode + " is detected for " + treeWalkInfo(tw));
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
  public byte[] getContent(@NotNull VcsModification vcsModification,
                           @NotNull VcsChangeInfo change,
                           @NotNull VcsChangeInfo.ContentType contentType,
                           @NotNull VcsRoot vcsRoot)
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
  public byte[] getContent(@NotNull String filePath, @NotNull VcsRoot versionedRoot, @NotNull String version) throws VcsException {
    Settings s = createSettings(versionedRoot);
    try {
      Repository r = getRepository(s);
      try {
        LOG.info("Getting data from " + version + ":" + filePath + " for " + s.debugInfo());
        final String rev = GitUtils.versionRevision(version);
        Commit c = ensureCommitLoaded(s, r, rev);
        Tree t = c.getTree();
        TreeEntry e = t.findBlobMember(filePath);
        if (e == null) {
          throw new VcsException("The file " + filePath + " could not be found in " + rev + s.debugInfo());
        }
        ObjectId id = e.getId();
        final ObjectLoader loader = r.openBlob(id);
        final byte[] data = loader.getBytes();
        LOG.info(
          "File retrived " + version + ":" + filePath + " (hash = " + id.name() + ", length = " + data.length + ") for " + s.debugInfo());
        return data;
      } finally {
        r.close();
      }
    } catch (Exception e) {
      throw processException("retriving content", e);
    }
  }

  /**
   * Ensure that the specified commit is loaded in the repository
   *
   * @param s   the settings
   * @param r   the repository
   * @param rev the revision to fetch
   * @return the mapped commit
   * @throws Exception in case of IO problem
   */
  private Commit ensureCommitLoaded(Settings s, Repository r, String rev) throws Exception {
    Commit c = null;
    try {
      c = r.mapCommit(rev);
    } catch (IOException ex) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("IO problem for commit " + rev + " in " + s.debugInfo(), ex);
      }
    }
    if (c == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Commit " + rev + " is not in the repository for " + s.debugInfo() + ", fetching data... ");
      }
      fetchBranchData(s, r);
      c = r.mapCommit(rev);
      if (c == null) {
        throw new VcsException("The version name could not be resolved " + rev + "(" + s.getPublicURL() + "#" + s.getBranch() + ")");
      }
    }
    return c;
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
    return "Git (JetBrains)";
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
    final String branch = root.getProperty(Constants.BRANCH_NAME);
    return root.getProperty(Constants.URL) + "#" + (branch == null ? "master" : branch);
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
    return GitUtils.VERSION_COMPARATOR;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public String getCurrentVersion(@NotNull VcsRoot root) throws VcsException {
    Settings s = createSettings(root);
    try {
      Repository r = getRepository(s);
      try {
        fetchBranchData(s, r);
        String refName = GitUtils.branchRef(s.getBranch());
        Commit c = r.mapCommit(refName);
        if (c == null) {
          throw new VcsException("The branch name could not be resolved " + refName);
        }
        LOG.info("The current version is " + c.getCommitId().name() + " " + s.debugInfo());
        return GitUtils.makeVersion(c);
      } finally {
        r.close();
      }
    } catch (Exception e) {
      throw processException("retriving current version", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean sourcesUpdatePossibleIfChangesNotFound(@NotNull VcsRoot root) {
    return true;
  }

  /**
   * Fetch data for the branch
   *
   * @param settings   settings for the root
   * @param repository the repository
   * @throws Exception if there is a problem with fetching data
   */
  private void fetchBranchData(Settings settings, Repository repository) throws Exception {
    final String refName = GitUtils.branchRef(settings.getBranch());
    final Transport tn = openTransport(settings, repository);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Fetching data for " + refName + "... " + settings.debugInfo());
    }
    try {
      RefSpec spec = new RefSpec().setSource(refName).setDestination(refName).setForceUpdate(true);
      tn.fetch(NullProgressMonitor.INSTANCE, Collections.singletonList(spec));
    } finally {
      tn.close();
    }
  }

  /**
   * {@inheritDoc}
   */
  public String testConnection(@NotNull VcsRoot vcsRoot) throws VcsException {
    Settings s = createSettings(vcsRoot);
    try {
      Repository r = getRepository(s);
      try {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Openning connection for " + s.debugInfo());
        }
        final Transport tn = openTransport(s, r);
        try {
          final FetchConnection c = tn.openFetch();
          try {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Checking references... " + s.debugInfo());
            }
            String refName = GitUtils.branchRef(s.getBranch());
            for (final Ref ref : c.getRefs()) {
              if (refName.equals(ref.getName())) {
                LOG.info("The branch reference found " + refName + "=" + ref.getObjectId() + " for " + s.debugInfo());
                return null;
              }
            }
            throw new VcsException("The branch " + refName + " was not found in the repository " + s.getPublicURL());
          } finally {
            c.close();
          }
        } finally {
          tn.close();
        }
      } finally {
        r.close();
      }
    } catch (Exception e) {
      throw processException("connection test", e);
    }
  }

  /**
   * Create settings object
   *
   * @param vcsRoot the root object
   * @return the created object
   * @throws VcsException in case of IO problems
   */
  private Settings createSettings(VcsRoot vcsRoot) throws VcsException {
    final Settings settings = new Settings(vcsRoot);
    if (settings.getRepositoryPath() == null) {
      String url = settings.getRepositoryURL().toString();
      File dir = new File(myServerPaths.getCachesDir());
      String name = String.format("git-%08X.git", url.hashCode() & 0xFFFFFFFFL);
      settings.setRepositoryPath(new File(dir, "git" + File.separatorChar + name));
      if (LOG.isDebugEnabled()) {
        LOG.debug("Using internal directory for " + settings.debugInfo());
      }
    }
    return settings;
  }

  /**
   * {@inheritDoc}
   */
  public LabelingSupport getLabelingSupport() {
    return this;
  }

  @NotNull
  public VcsFileContentProvider getContentProvider() {
    return this;
  }

  @NotNull
  public CollectChangesPolicy getCollectChangesPolicy() {
    return this;
  }

  @NotNull
  public BuildPatchPolicy getBuildPatchPolicy() {
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public String label(@NotNull String label, @NotNull String version, @NotNull VcsRoot root, @NotNull CheckoutRules checkoutRules)
    throws VcsException {
    Settings s = createSettings(root);
    try {
      Repository r = getRepository(s);
      try {
        final ObjectId rev = versionObjectId(version);
        ensureCommitLoaded(s, r, rev.name());
        Tag t = new Tag(r);
        t.setTag(label);
        t.setObjId(rev);
        t.tag();
        String tagRef = GitUtils.tagName(label);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Tag created  " + label + "=" + version + " for " + s.debugInfo());
        }
        final Transport tn = openTransport(s, r);
        try {
          final PushConnection c = tn.openPush();
          try {
            RemoteRefUpdate ru = new RemoteRefUpdate(r, tagRef, tagRef, false, null, null);
            c.push(NullProgressMonitor.INSTANCE, Collections.singletonMap(tagRef, ru));
            LOG.info("Tag  " + label + "=" + version + " pushed with status " + ru.getStatus() + " for " + s.debugInfo());
            switch (ru.getStatus()) {
              case UP_TO_DATE:
              case OK:
                break;
              default:
                throw new VcsException("The remote tag was not created (" + ru.getStatus() + "): " + label);
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
    } catch (Exception e) {
      throw processException("labelling", e);
    }
  }

  /**
   * Get repository using settings objct
   *
   * @param s settings object
   * @return the repository instance
   * @throws VcsException if the repository could not be accessed
   */
  private static Repository getRepository(Settings s) throws VcsException {
    return GitUtils.getRepository(s.getRepositoryPath(), s.getRepositoryURL());
  }

  /**
   * Open transport for the repository
   *
   * @param s the vcs settings
   * @param r the repository to open
   * @return the transport instance
   * @throws NotSupportedException if transport is not supported
   * @throws URISyntaxException    if URI is incorrect syntax
   * @throws VcsException          if there is a problem with configuring the transport
   */
  private Transport openTransport(Settings s, Repository r) throws NotSupportedException, URISyntaxException, VcsException {
    final Transport t = Transport.open(r, s.getRepositoryURL());
    if (t instanceof SshTransport) {
      SshTransport ssh = (SshTransport)t;
      ssh.setSshSessionFactory(getSshSessionFactory(s));
    }
    return t;
  }

  /**
   * Get appropriate session factory object using settings
   *
   * @param s a vcs root settings
   * @return session factory object
   * @throws VcsException in case of problems with creating object
   */
  private SshSessionFactory getSshSessionFactory(Settings s) throws VcsException {
    switch (s.getAuthenticationMethod()) {
      case PRIVATE_KEY_DEFAULT:
        return mySshSessionFactory;
      case PRIVATE_KEY_FILE:
        return new PrivateKeyFileSshSessionFactory(s);
      case PASSWORD:
        return PasswordSshSessionFactory.INSTANCE;
      default:
        throw new VcsException("The authentication method " + s.getAuthenticationMethod() + " is not supported for SSH");
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
