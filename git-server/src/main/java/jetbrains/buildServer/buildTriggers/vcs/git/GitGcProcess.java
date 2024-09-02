

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.eclipse.jgit.api.GarbageCollectCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.TextProgressMonitor;

import java.io.File;
import java.io.InterruptedIOException;
import java.io.PrintWriter;

public class GitGcProcess {

  public static void main(String... args) throws Exception {
    GitServerUtil.configureExternalProcessLogger(false);
    try {
      String gitDir = args[0];
      System.out.println("run gc in " + gitDir);
      Repository r = new RepositoryBuilder().setBare().setGitDir(new File(gitDir)).build();
      Git git = new Git(r);
      GarbageCollectCommand gc = git.gc();
      gc.setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)));
      gc.call();
    } catch (Throwable t) {
      if (isImportant(t)) {
        t.printStackTrace(System.err);
      } else {
        System.err.println(t.getMessage());
      }
      System.exit(1);
    }
  }

  private static boolean isImportant(Throwable t) {
    return t instanceof NullPointerException ||
           t instanceof Error ||
           t instanceof InterruptedException ||
           t instanceof InterruptedIOException;
  }
}