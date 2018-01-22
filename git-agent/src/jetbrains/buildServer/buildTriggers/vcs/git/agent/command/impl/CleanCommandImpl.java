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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.AgentCleanFilesPolicy;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.CleanCommand;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.Predicate;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CleanCommandImpl extends BaseCommandImpl implements CleanCommand {

  private AgentCleanFilesPolicy myCleanPolicy = AgentCleanFilesPolicy.ALL_UNTRACKED;

  public CleanCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public CleanCommand setCleanPolicy(@NotNull AgentCleanFilesPolicy policy) {
    myCleanPolicy = policy;
    return this;
  }

  public void call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameters("clean", "-f", "-d");
    switch (myCleanPolicy) {
      case ALL_UNTRACKED:
        cmd.addParameter("-x");
        break;
      case IGNORED_ONLY:
        cmd.addParameter("-X");
        break;
      case NON_IGNORED_ONLY:
        break;
    }
    try {
      ExecResult r = CommandUtil.runCommand(cmd);
      CommandUtil.failIfNotEmptyStdErr(cmd, r);
    } catch (VcsException e) {
      Loggers.VCS.warn("Failed to clean files");
      if (!SystemInfo.isWindows || CommandUtil.isCanceledError(e))
        throw e;
      File workingDir = cmd.getWorkingDirectory();
      if (workingDir == null)
        throw e;
      handleLongFileNames(workingDir, e);
    }
  }


  private void handleLongFileNames(@NotNull File rootDir, @NotNull VcsException originalError) throws VcsException {
    List<String> files = new ArrayList<String>();
    FileUtil.listFilesRecursively(rootDir, "", true, Integer.MAX_VALUE, new Predicate<File>() {
      public boolean apply(File f) {
        return true;
      }
    }, files);

    int targetDirLength = rootDir.getAbsolutePath().length();
    List<String> longFileNames = new ArrayList<String>();
    for (String f : files) {
      if (targetDirLength + 1 + f.length() >= 256)
        longFileNames.add(f);
    }

    if (longFileNames.isEmpty()) {
      Loggers.VCS.info("No files with long names found");
      throw originalError;
    } else {
      Loggers.VCS.info(longFileNames.size() + " files with long names found:");
      for (String f : longFileNames) {
        Loggers.VCS.info(f);
      }
      Loggers.VCS.info("Try removing files with long names manually");
    }

    Repository repository = null;
    try {
      repository = new RepositoryBuilder().setWorkTree(rootDir).build();
      WorkingDirStatus status = getWorkingDirStatus(repository);
      Set<String> untracked = status.getUntracked();
      for (String f : longFileNames) {
        if (untracked.contains(f) || status.isIgnored(f)) {
          FileUtil.delete(new File(rootDir, f));
          Loggers.VCS.info(f + " is removed");
        } else {
          Loggers.VCS.info("The file " + f + " is tracked, don't remove it");
        }
      }
    } catch (Exception e1) {
      Loggers.VCS.error("Error while cleaning files with long names", e1);
    } finally {
      if (repository != null)
        repository.close();
    }
  }


  @NotNull
  private WorkingDirStatus getWorkingDirStatus(@NotNull Repository repo) {
    FileTreeIterator workingTreeIt = new FileTreeIterator(repo);
    try {
      IndexDiff diff = new IndexDiff(repo, Constants.HEAD, workingTreeIt);
      diff.diff();
      return new WorkingDirStatus(diff);
    } catch (IOException e) {
      throw new JGitInternalException(e.getMessage(), e);
    }
  }

  private static class WorkingDirStatus extends Status {
    private IndexDiff myDiff;
    private WorkingDirStatus(IndexDiff diff) {
      super(diff);
      myDiff = diff;
    }

    boolean isIgnored(@NotNull String file) {
      for (String prefix : myDiff.getIgnoredNotInIndex()) {
        if (file.startsWith(prefix))
          return true;
      }
      return false;
    }
  }
}
