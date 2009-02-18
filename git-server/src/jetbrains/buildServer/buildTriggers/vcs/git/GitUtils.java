package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.RepositoryConfig;

import java.io.File;
import java.util.Comparator;

/**
 * Commands that allows working with git repositories
 */
public class GitUtils {

    /**
     * The version comparator
     */
    public static final Comparator<String> VERSION_COMPATOR = new Comparator<String>() {
        public int compare(String o1, String o2) {
            long r = versionTime(o1) - versionTime(o2);
            return r<0?-1:r>0?1:0;
        }
    };

    /**
     * Make version string from revision hash and time
     *
     * @param revision the revision hash
     * @param time     the time of revision
     * @return the version string
     */
    @NotNull
    public static String makeVersion(@NotNull String revision, long time) {
        return revision + "@" + Long.toHexString(time);
    }

    /**
     * Extract revision number from the version
     *
     * @param version string
     * @return the revision number
     */
    @NotNull
    public static String versionRevision(@NotNull String version) {
        int i = version.indexOf('@');
        if(i == -1) {
            throw new IllegalArgumentException("Invalid format of version: "+version);
        }
        return version.substring(0, i);
    }

    /**
     * Extract revision number from the version
     *
     * @param version string
     * @return the revision number
     */
    public static long versionTime(@NotNull String version) {
        int i = version.indexOf('@');
        if(i == -1) {
            throw new IllegalArgumentException("Invalid format of version: "+version);
        }
        return Long.parseLong(version.substring(i+1),16);
    }

    /**
     * Ensures that a bare repository exists at the specified path.
     * If it does not, the directory is attempted to be created.
     *
     * @param dir the path to the directory to init
     * @return a connection to repository
     * @throws VcsException if the there is a problem with accessing VCS
     */
    public static Repository getRepository(File dir) throws VcsException {
        final File parentFile = dir.getParentFile();
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                throw new VcsException("Unable to created parent directory: " + parentFile);
            }
        }
        boolean create = !dir.exists();
        if (!create && !dir.isDirectory()) {
            throw new VcsException("The specified path is not a directory: " + dir);
        }
        try {
            Repository r = new Repository(dir);
            if (create) {
                r.create();
                final RepositoryConfig config = r.getConfig();
                config.setString("core", null, "bare", "true");
                config.save();
            }
            return r;
        } catch (Exception ex) {
            throw new VcsException("The repository at " + dir + " cannot be openned or created: " + ex);
        }
    }

    /**
     * Create reference name from branch name
     * @param branch the branch name
     * @return the reference name
     */
    public static String branchRef(String branch) {
        return "refs/heads/" + branch;
    }
}
