package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.lib.*;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevSort;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.transport.FetchConnection;
import org.spearce.jgit.transport.RefSpec;
import org.spearce.jgit.transport.Transport;
import org.spearce.jgit.treewalk.TreeWalk;
import org.spearce.jgit.treewalk.filter.TreeFilter;

import java.io.File;
import java.io.IOException;
import java.util.*;


/**
 * Git VCS support
 */
public class GitVcsSupport extends VcsSupport {
    /**
     * Amount of characters disiplayed for in the display version of revision number
     */
    private static final int DISPLAY_VERSION_AMOUNT = 8;
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
    public List<ModificationData> collectBuildChanges(VcsRoot root, @NotNull String fromVersion, @NotNull String currentVersion, CheckoutRules checkoutRules) throws VcsException {
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
                try {
                    final RevCommit fromRev = revs.parseCommit(ObjectId.fromString(from));
                    revs.markUninteresting(fromRev);
                    RevCommit c;
                    while ((c = revs.next()) != null) {
                        Commit cc = c.asCommit(revs);
                        final ObjectId[] parentIds = cc.getParentIds();
                        String cv = GitUtils.makeVersion(cc);
                        String pv = parentIds.length == 0 ? GitUtils.makeVersion(ObjectId.zeroId().name(), 0) : GitUtils.makeVersion(r.mapCommit(cc.getParentIds()[0]));
                        List<VcsChange> changes = getCommitChanges(r, cc, parentIds, cv, pv);
                        ModificationData m = new ModificationData(cc.getCommitter().getWhen(), changes, cc.getMessage(), GitUtils.getUser(cc), root, cv, GitUtils.displayVersion(cc));
                        rc.add(m);
                    }
                } catch (MissingObjectException ex) {
                    // TODO commit not found should be handled in orther way
                    throw new VcsException("Unalble to resolve previous commit: " + fromVersion, ex);
                }
            } finally {
                r.close();
            }
        } catch (VcsException e) {
            throw e;
        } catch (Exception e) {
            throw new VcsException("The get content failed: " + e, e);
        }
        // TODO checkout rules are ignored right now
        return rc;
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
                FileMode m = tw.getFileMode(i);
                if (FileMode.MISSING.equals(tw.getFileMode(i))) {
                    // the delete merge
                    return ChangeType.UNCHANGED;
                }
            }
            return ChangeType.DELETED;
        }
        boolean fileAdded = true;
        for (int i = 1; i < nTrees; i++) {
            FileMode m = tw.getFileMode(i);
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

    public void buildPatch(@NotNull VcsRoot root, @Nullable String fromVersion, @NotNull String toVersion, @NotNull PatchBuilder builder, @NotNull CheckoutRules checkoutRules) throws IOException, VcsException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    public byte[] getContent(VcsModification vcsModification, VcsChangeInfo change, VcsChangeInfo.ContentType contentType, VcsRoot vcsRoot) throws VcsException {
        String version = contentType == VcsChangeInfo.ContentType.BEFORE_CHANGE ? change.getBeforeChangeRevisionNumber() : change.getAfterChangeRevisionNumber();
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

    public PropertiesProcessor getVcsPropertiesProcessor() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    public String getVcsSettingsJspFilePath() {
        return "gitSettings.jsp";
    }

    @NotNull
    public String describeVcsRoot(VcsRoot vcsRoot) {
        Settings s = new Settings(vcsRoot);
        return s.getRepositoryURL() + "#" + s.getBranch();
    }

    public Map<String, String> getDefaultVcsProperties() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * {@inheritDoc}
     */
    public String getVersionDisplayName(@NotNull String version, @NotNull VcsRoot root) throws VcsException {
        return version.substring(DISPLAY_VERSION_AMOUNT, 0);
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
    private Settings createSettings(VcsRoot vcsRoot) {
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
