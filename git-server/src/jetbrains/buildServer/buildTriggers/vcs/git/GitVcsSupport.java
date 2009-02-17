package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import jetbrains.buildServer.serverSide.PropertiesProcessor;

import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Git VCS support
 */
public class GitVcsSupport extends VcsSupport {

    public List<ModificationData> collectBuildChanges(VcsRoot root, @NotNull String fromVersion, @NotNull String currentVersion, CheckoutRules checkoutRules) throws VcsException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isTestConnectionSupported() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
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

    @NotNull
    public String getName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @NotNull
    public String getDisplayName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public PropertiesProcessor getVcsPropertiesProcessor() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @NotNull
    public String getVcsSettingsJspFilePath() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @NotNull
    public String describeVcsRoot(VcsRoot vcsRoot) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Map<String, String> getDefaultVcsProperties() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getVersionDisplayName(@NotNull String version, @NotNull VcsRoot root) throws VcsException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @NotNull
    public Comparator<String> getVersionComparator() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @NotNull
    public String getCurrentVersion(@NotNull VcsRoot root) throws VcsException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String testConnection(@NotNull VcsRoot vcsRoot) throws VcsException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
