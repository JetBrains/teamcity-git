package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.NullProgressMonitor;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.transport.FetchConnection;
import org.spearce.jgit.transport.RefSpec;
import org.spearce.jgit.transport.Transport;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;


/**
 * Git VCS support
 */
public class GitVcsSupport extends VcsSupport {
    /**
     * Amount of characters disiplayed for in the display version of revision number
     */
    private static final int DISPLAY_VERSION_AMOUNT = 8;

    public List<ModificationData> collectBuildChanges(VcsRoot root, @NotNull String fromVersion, @NotNull String currentVersion, CheckoutRules checkoutRules) throws VcsException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
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

    @NotNull
    public byte[] getContent(VcsModification vcsModification, VcsChangeInfo change, VcsChangeInfo.ContentType contentType, VcsRoot vcsRoot) throws VcsException {
        return new byte[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    @NotNull
    public byte[] getContent(String filePath, VcsRoot versionedRoot, String version) throws VcsException {
        return new byte[0];  //To change body of implemented methods use File | Settings | File Templates.
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
        Settings s = new Settings(root);
        try {
            Repository r = GitUtils.getRepository(s.getRepositoryPath());
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
                return GitUtils.makeVersion(c.getCommitId().name(), c.getCommitter().getWhen().getTime());
            } finally {
                r.close();
            }
        } catch (Exception e) {
            throw new VcsException("The current version failed: " + e, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String testConnection(@NotNull VcsRoot vcsRoot) throws VcsException {
        Settings s = new Settings(vcsRoot);
        try {
            Repository r = GitUtils.getRepository(s.getRepositoryPath());
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
        } catch (Exception e) {
            throw new VcsException("Repository test failed: " + e, e);
        }
    }
}
