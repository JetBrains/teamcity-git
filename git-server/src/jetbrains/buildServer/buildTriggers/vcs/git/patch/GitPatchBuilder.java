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

package jetbrains.buildServer.buildTriggers.vcs.git.patch;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleAwareTreeIterator;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class GitPatchBuilder {

  private final static Logger LOG = Logger.getInstance(GitPatchBuilder.class.getName());

  private final OperationContext myContext;
  private final GitVcsRoot myGitRoot;
  private final PatchBuilder myBuilder;
  private final List<Callable<Void>> myActions = new ArrayList<Callable<Void>>();
  private final String myFromRevision;
  private final String myToRevision;
  private final CheckoutRules myRules;
  private final PatchFileAction myFileAction;
  private boolean myFullCheckout;
  private BuildPatchLogger myLogger;
  private Repository myRepository;
  private VcsChangeTreeWalk myTreeWalk;
  private final boolean myVerboseTreeWalkLog;

  public GitPatchBuilder(@NotNull OperationContext context,
                         @NotNull PatchBuilder builder,
                         @Nullable String fromRevision,
                         @NotNull String toRevision,
                         @NotNull CheckoutRules rules,
                         boolean verboseTreeWalkLog) throws VcsException {
    this(context, builder, fromRevision, toRevision, rules, verboseTreeWalkLog, new PatchFileAction());
  }

  public GitPatchBuilder(@NotNull OperationContext context,
                         @NotNull PatchBuilder builder,
                         @Nullable String fromRevision,
                         @NotNull String toRevision,
                         @NotNull CheckoutRules rules,
                         boolean verboseTreeWalkLog,
                         @NotNull PatchFileAction patchFileAction) throws VcsException {
    myContext = context;
    myGitRoot = context.getGitRoot();
    myBuilder = builder;
    myFromRevision = fromRevision;
    myToRevision = toRevision;
    myRules = rules;
    myFullCheckout = fromRevision == null;
    myTreeWalk = null;
    myVerboseTreeWalkLog = verboseTreeWalkLog;
    myFileAction = patchFileAction;
  }

  public void buildPatch() throws Exception {
    myLogger = new BuildPatchLogger(LOG, myGitRoot.debugInfo(), myVerboseTreeWalkLog);
    myRepository = myContext.getRepository();
    try {
      myTreeWalk = new VcsChangeTreeWalk(newObjectReaderForTree(), myGitRoot.debugInfo(), myVerboseTreeWalkLog);
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

  @NotNull
  protected ObjectReader newObjectReaderForTree() {
    return myRepository.newObjectReader();
  }

  private void addToCommitTree() throws IOException, VcsException {
    RevCommit toCommit = myContext.findCommit(myRepository, myToRevision);
    if (toCommit == null)
      throw new VcsException("Cannot find commit " + myToRevision + " in repository " + myRepository.getDirectory().getAbsolutePath());
    myContext.addTree(myGitRoot, myTreeWalk, myRepository, toCommit, false, true, myRules);
  }

  private void addFromCommitTree() throws IOException, VcsException {
    if (myFullCheckout) {
      myLogger.logBuildCleanPatch(myToRevision);
      myTreeWalk.addTree(new EmptyTreeIterator());
    } else {
      assert myFromRevision != null;
      myLogger.logBuildIncrementalPatch(myFromRevision, myToRevision);
      RevCommit fromCommit = myContext.findCommit(myRepository, myFromRevision);
      if (fromCommit == null) {
        myLogger.logFromRevisionNotFound(myFromRevision);
        myTreeWalk.addTree(new EmptyTreeIterator());
        myFullCheckout = true;
      } else {
        myContext.addTree(myGitRoot, myTreeWalk, myRepository, fromCommit, true, true, myRules);
      }
    }
  }

  private void walkTree() throws Exception {
    while (myTreeWalk.next()) {
      String path = myTreeWalk.getPathString();
      String mappedPath = myRules.map(path);
      if (mappedPath == null) {
        myLogger.logFileExcludedByCheckoutRules(path, myRules);
        continue;
      }
      if (myLogger.isDebugEnabled()) {
        myLogger.logVisitFile(myTreeWalk.treeWalkInfo(path));
      }
      ChangeType changeType = myTreeWalk.classifyChange();
      myLogger.logChangeType(path, changeType);
      switch (changeType) {
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

  private void changeOrCreateFile(@NotNull String path, @NotNull String mappedPath) throws Exception {
    String mode = myTreeWalk.getModeDiff();
    if (mode != null)
      myLogger.logFileModeChanged(mode, myTreeWalk.treeWalkInfo(path));
    ObjectId id = myTreeWalk.getObjectId(0);
    LoadContentAction loadContent = getLoadContentAction(path, mappedPath, mode, id);
    if (myFullCheckout) {
      loadContent.call();// full checkout, we aren't going to see any deletes
    } else {
      myFileAction.call("-", mappedPath);
      myActions.add(loadContent);
    }
  }

  private static final ContentLoaderFactory CONTENT_LOADER_FACTORY = new ContentLoaderFactory() {
    @Nullable
    public ObjectLoader open(@NotNull final Repository repo, @NotNull final ObjectId id) throws IOException {
      return repo.open(id);
    }
  };


  @NotNull
  protected LoadContentAction getLoadContentAction(@NotNull final String path,
                                                   @NotNull final String mappedPath,
                                                   final String mode,
                                                   @NotNull final ObjectId id) {
    final Repository repository = getRepositoryOfTree();
    return new LoadContentAction(
      contentLoaderFactory(),
      myGitRoot,
      myBuilder,
      myLogger,
      myFileAction,
      repository,
      id,
      path,
      mappedPath,
      mode);
  }

  @NotNull
  protected ContentLoaderFactory contentLoaderFactory() {
    return CONTENT_LOADER_FACTORY;
  }

  private void deleteFile(@NotNull String mappedFile) throws IOException {
    myFileAction.call("DELETE", mappedFile);
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

}
