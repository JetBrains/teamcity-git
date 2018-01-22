/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
