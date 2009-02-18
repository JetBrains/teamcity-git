package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.RepositoryConfig;

import java.io.File;

/**
 * Commands that allows working with git repositories
 */
public class GitUtils {

    /**
     * Ensures that a bare repository exists at the specified path.
     * If it does not, the directory is attempted to be created.
     *
     * @param dir the path to the directory to init
     * @return a connection to repository
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
            throw new VcsException("The repository at " + dir + " cannot be openned or created: "+ex);
        }
    }
}
