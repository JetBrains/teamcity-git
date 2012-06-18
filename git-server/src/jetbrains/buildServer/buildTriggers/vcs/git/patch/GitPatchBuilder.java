/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.patch;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleAwareTreeIterator;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;


/**
* @author dmitry.neverov
*/
public final class GitPatchBuilder {

  private final static Logger LOG = Logger.getInstance(GitPatchBuilder.class.getName());

  private final ServerPluginConfig myConfig;
  private final OperationContext myContext;
  private final GitVcsRoot myGitRoot;
  private final PatchBuilder myBuilder;
  private final List<Callable<Void>> myActions = new ArrayList<Callable<Void>>();
  private final String myFromRevision;
  private final String myToRevision;
  private final CheckoutRules myRules;
  private boolean myCleanCheckout;
  private BuildPatchLogger myLogger;
  private Repository myRepository;
  private VcsChangeTreeWalk myTreeWalk;

  public GitPatchBuilder(@NotNull ServerPluginConfig config,
                         @NotNull OperationContext context,
                         @NotNull PatchBuilder builder,
                         @Nullable String fromRevision,
                         @NotNull String toRevision,
                         @NotNull CheckoutRules rules) throws VcsException {
    myConfig = config;
    myContext = context;
    myGitRoot = context.getGitRoot();
    myBuilder = builder;
    myFromRevision = fromRevision;
    myToRevision = toRevision;
    myRules = rules;
    myCleanCheckout = fromRevision == null;
    myTreeWalk = null;
  }

  public void buildPatch() throws Exception {
    myLogger = new BuildPatchLogger(LOG, myGitRoot.debugInfo(), myConfig);
    myRepository = myContext.getRepository();
    try {
      myTreeWalk = new VcsChangeTreeWalk(myRepository, myGitRoot.debugInfo());
      myTreeWalk.setFilter(TreeFilter.ANY_DIFF);
      myTreeWalk.setRecursive(true);
      addToCommitTree();
      addFromCommitTree();
      walkTree();
      finish();
    } finally {
      if (myTreeWalk != null)
        myTreeWalk.release();
    }
  }

  private void addToCommitTree() throws IOException, VcsException {
    RevCommit toCommit = myContext.findCommit(myRepository, myToRevision);
    myContext.addTree(myGitRoot, myTreeWalk, myRepository, toCommit, false);
  }

  private void addFromCommitTree() throws IOException, VcsException {
    if (myCleanCheckout) {
      myLogger.logBuildCleanPatch(myToRevision);
      myTreeWalk.addTree(new EmptyTreeIterator());
    } else {
      myLogger.logBuildIncrementalPatch(myFromRevision, myToRevision);
      RevCommit fromCommit = myContext.findCommit(myRepository, myFromRevision);
      if (fromCommit == null) {
        myLogger.logFromRevisionNotFound(myFromRevision);
        myTreeWalk.addTree(new EmptyTreeIterator());
        cleanCheckoutDir();
        myCleanCheckout = true;
      } else {
        myContext.addTree(myGitRoot, myTreeWalk, myRepository, fromCommit, true);
      }
    }
  }

  private void walkTree() throws Exception {
    while (myTreeWalk.next()) {
      String path = myTreeWalk.getPathString();
      String mappedPath = myRules.map(path);
      if (mappedPath == null)
        continue;
      myLogger.logVisitFile(myTreeWalk.treeWalkInfo(path));
      switch (myTreeWalk.classifyChange()) {
        case UNCHANGED:
          break;
        case MODIFIED:
        case ADDED:
        case FILE_MODE_CHANGED:
          if (!FileMode.GITLINK.equals(myTreeWalk.getFileMode(0)))
            changeOrCreateFile(path, mappedPath);
          break;
        case DELETED:
          if (!FileMode.GITLINK.equals(myTreeWalk.getFileMode(0)))
            deleteFile(mappedPath);
          break;
        default:
          throw new IllegalStateException("Unknown change type");
      }
    }
  }

  private void cleanCheckoutDir() throws IOException {
    myBuilder.deleteDirectory(new File(myRules.map("")), true);
  }

  private void changeOrCreateFile(@NotNull String path, @NotNull String mappedPath) throws Exception {
    String mode = myTreeWalk.getModeDiff();
    if (mode != null)
      myLogger.logFileModeChanged(mode, myTreeWalk.treeWalkInfo(path));
    ObjectId id = myTreeWalk.getObjectId(0);
    Repository r = getRepositoryOfTree();
    LoadContentAction loadContent = loadContent().fromRepository(r)
      .withObjectId(id)
      .withMode(mode)
      .withPath(path)
      .withMappedPath(mappedPath);
    if (myCleanCheckout) {
      loadContent.call();// clean patch, we aren't going to see any deletes
    } else {
      myActions.add(loadContent);
    }
  }

  private void deleteFile(@NotNull String mappedFile) throws IOException {
    myBuilder.deleteFile(GitUtils.toFile(mappedFile), true);
  }

  private void finish() throws Exception {
    for (Callable<Void> action : myActions)
      action.call();
  }

  private Repository getRepositoryOfTree() {
    Repository result;
    AbstractTreeIterator ti = myTreeWalk.getTree(0, AbstractTreeIterator.class);
    if (ti instanceof SubmoduleAwareTreeIterator) {
      result = ((SubmoduleAwareTreeIterator)ti).getRepository();
    } else {
      result = myRepository;
    }
    return result;
  }

  private LoadContentAction loadContent() {
    return new LoadContentAction(myBuilder, myLogger);
  }
}
