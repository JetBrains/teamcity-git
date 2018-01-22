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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static java.util.Arrays.asList;

/**
* @author dmitry.neverov
*/
public class GitLabelingSupport implements LabelingSupport {

  private final static Logger LOG = Logger.getInstance(GitLabelingSupport.class.getName());

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final RepositoryManager myRepositoryManager;
  private final TransportFactory myTransportFactory;
  private final ServerPluginConfig myConfig;

  public GitLabelingSupport(@NotNull GitVcsSupport vcs,
                            @NotNull CommitLoader commitLoader,
                            @NotNull RepositoryManager repositoryManager,
                            @NotNull TransportFactory transportFactory,
                            @NotNull ServerPluginConfig config) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myRepositoryManager = repositoryManager;
    myTransportFactory = transportFactory;
    myConfig = config;
  }

  @NotNull
  public String label(@NotNull String label,
                      @NotNull String version,
                      @NotNull VcsRoot root,
                      @NotNull CheckoutRules checkoutRules) throws VcsException {
    OperationContext context = myVcs.createContext(root, "labeling");
    GitVcsRoot gitRoot = context.getGitRoot();
    return myRepositoryManager.runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      RevisionsInfo revisionsInfo = new RevisionsInfo();
      if (myConfig.useTagPackHeuristics()) {
        LOG.debug("Update repository before labeling " + gitRoot.debugInfo());
        RepositoryStateData currentState = myVcs.getCurrentState(gitRoot);
        if (!myConfig.analyzeTagsInPackHeuristics())
          currentState = excludeTags(currentState);
        try {
          myVcs.getCollectChangesPolicy().ensureRepositoryStateLoadedFor(context, context.getRepository(), false, currentState);
        } catch (Exception e) {
          LOG.debug("Error while updating repository " + gitRoot.debugInfo(), e);
        }
        revisionsInfo = new RevisionsInfo(currentState);
      }
      try {
        long start = System.currentTimeMillis();
        Repository r = context.getRepository();
        String commitSHA = GitUtils.versionRevision(version);
        RevCommit commit = myCommitLoader.loadCommit(context, gitRoot, commitSHA);
        Git git = new Git(r);
        Ref tagRef = git.tag().setTagger(gitRoot.getTagger(r))
          .setName(label)
          .setObjectId(commit)
          .call();
        if (tagRef.getObjectId() == null || resolve(r, tagRef) == null) {
          LOG.warn("Tag's " + tagRef.getName() + " objectId " + (tagRef.getObjectId() != null ? tagRef.getObjectId().name() + " " : "") + "cannot be resolved");
        } else if (LOG.isDebugEnabled()) {
          LOG.debug("Tag created  " + label + "=" + version + " for " + gitRoot.debugInfo() +
                    " in " + (System.currentTimeMillis() - start) + "ms");
        }
        return push(label, version, gitRoot, r, tagRef, revisionsInfo);
      } catch (Exception e) {
        throw context.wrapException(e);
      } finally {
        context.close();
      }
    });
  }

  @NotNull
  private String push(@NotNull String label,
                      @NotNull String version,
                      @NotNull GitVcsRoot gitRoot,
                      @NotNull Repository r,
                      @NotNull Ref tagRef,
                      @NotNull RevisionsInfo revisionsInfo) throws VcsException, IOException {
    long pushStart = System.currentTimeMillis();
    final Transport tn = myTransportFactory.createTransport(r, gitRoot.getRepositoryPushURL(), gitRoot.getAuthSettings(), myConfig.getPushTimeoutSeconds());
    PushConnection c = null;
    try {
      c = tn.openPush();
      RemoteRefUpdate ru = new RemoteRefUpdate(r, tagRef.getName(), tagRef.getObjectId(), tagRef.getName(), false, null, null);
      PreparePackFunction preparePack = null;
      if (c instanceof BasePackPushConnection) {
        final RevTag tagObject = getTagObject(r, tagRef);
        if (tagObject != null) {
          preparePack = new PreparePackFunction(tagObject, revisionsInfo);
          ((BasePackPushConnection)c).setPreparePack(preparePack);
        } else {
          LOG.debug("Cannot locate the " + tagRef.getName() + " tag object, don't use pack heuristic");
        }
      }
      c.push(NullProgressMonitor.INSTANCE, Collections.singletonMap(tagRef.getName(), ru));
      LOG.info("Tag  " + label + "=" + version + " was pushed with status " + ru.getStatus() + " for " + gitRoot.debugInfo() +
               " in " + (System.currentTimeMillis() - pushStart) + "ms" +
               (preparePack != null ? " (prepare pack " + preparePack.getPreparePackDurationMillis() + "ms)" : ""));
      switch (ru.getStatus()) {
        case UP_TO_DATE:
        case OK:
          break;
        default:
          String msg = ru.getMessage();
          throw new VcsException("The remote '" + label+ "' tag was not created" +
                                 ", status: " + ru.getStatus() +
                                 (!isEmpty(msg) ? ", message: " + msg : ""));
      }
      return label;
    } finally {
      if (c != null)
        c.close();
      tn.close();
    }
  }

  @Nullable
  private RevTag getTagObject(@NotNull Repository r, @NotNull Ref tagRef) {
    ObjectId tagId = tagRef.getObjectId();
    if (tagId == null)
      return null;
    RevWalk walk = new RevWalk(r);
    try {
      return walk.parseTag(tagId);
    } catch (Exception e) {
      return null;
    } finally {
      walk.release();
    }
  }

  @Nullable
  private ObjectId resolve(final Repository r, final Ref tagRef) {
    try {
      return r.resolve(tagRef.getName());
    } catch (IOException e) {
      return null;
    }
  }


  private class PreparePackFunction implements PreparePack {
    private final RevTag myTagObject;
    private final RevisionsInfo myRevisionsInfo;
    private long myPreparePackDurationMillis;

    public PreparePackFunction(@NotNull RevTag tagObject,
                               @NotNull RevisionsInfo revisionsInfo) {
      myTagObject = tagObject;
      myRevisionsInfo = revisionsInfo;
    }

    public void preparePack(ProgressMonitor monitor,
                            Repository repository,
                            PackWriter writer,
                            Set<ObjectId> want,
                            Set<ObjectId> have) throws IOException {
      long start = System.currentTimeMillis();
      boolean writeOnlyTag = canWriteOnlyTag(repository, have);
      if (writeOnlyTag) {
        writer.preparePack(asList((RevObject)myTagObject).iterator());
      } else {
        writer.preparePack(monitor, want, have);
      }
      myPreparePackDurationMillis = System.currentTimeMillis() - start;
    }


    private boolean canWriteOnlyTag(Repository repository, Set<ObjectId> have) {
      if (!myConfig.useTagPackHeuristics())
        return false;
      if (!myConfig.checkLabeledCommitIsInRemoteRepository())
        return true;
      RevWalk walk = new RevWalk(repository);
      try {
        RevObject taggedObject = walk.parseAny(myTagObject.getObject());
        if (taggedObject.getType() == org.eclipse.jgit.lib.Constants.OBJ_COMMIT) {
          RevCommit taggedCommit = walk.parseCommit(taggedObject);
          if (!remoteRepositoryContainsCommit(walk, taggedCommit, have)) {
            LOG.debug("Remote repository doesn't contain the tagged object " + myTagObject.getObject() +
                      ", use default prepare pack logic");
            if (myConfig.failLabelingWhenPackHeuristicsFails())
              throw new PackHeuristicsFailed("Remote repository doesn't contain the tagged object " + myTagObject.getObject());
            return false;
          }
          return true;
        } else {
          if (myConfig.failLabelingWhenPackHeuristicsFails())
            throw new PackHeuristicsFailed("Pack heuristics doesn't work when tagged object is not a commit");
          return false;
        }
      } catch (PackHeuristicsFailed e) {
        throw e;
      } catch (Exception e) {
        LOG.debug("Failed to determine if the tagged object " + myTagObject.getObject() +
                  " is present in the remote repository, use default prepare pack logic");
        if (myConfig.failLabelingWhenPackHeuristicsFails())
          throw new PackHeuristicsFailed("Failed to determine if the tagged object " + myTagObject.getObject() + " is present in the remote repository", e);
        return false;
      } finally {
        walk.release();
      }
    }


    private boolean remoteRepositoryContainsCommit(@NotNull RevWalk walk, @NotNull RevCommit commit, @NotNull Set<ObjectId> have) {
      try {
        for (RevCommit p : commit.getParents()) {
          walk.markUninteresting(p);
        }
      } catch (IOException e) {
        return false;
      }

      RevCommit c;
      for (ObjectId tip : myRevisionsInfo.getBranchRevisions(have)) {
        RevCommit tipCommit;
        try {
          tipCommit = walk.parseCommit(tip);
        } catch (Exception e) {
          continue;
        }
        try {
          walk.markStart(tipCommit);
        } catch (Exception e) {
          //ignore
        }
      }

      try {
        while ((c = walk.next()) != null) {
          if (c.equals(commit))
            return true;
        }
      } catch (Exception e) {
        return false;
      }

      return false;
    }


    public long getPreparePackDurationMillis() {
      return myPreparePackDurationMillis;
    }
  }


  private static class RevisionsInfo {
    private final LinkedHashSet<ObjectId> myRevisions = new LinkedHashSet<ObjectId>();
    private final boolean myIncludeAll;
    RevisionsInfo(@NotNull RepositoryStateData state) {
      String defaultBranchName = state.getDefaultBranchName();
      String defaultBranchRevision = state.getBranchRevisions().get(defaultBranchName);
      if (defaultBranchRevision != null)
        myRevisions.add(ObjectId.fromString(defaultBranchRevision));
      for (String revision : state.getBranchRevisions().values()) {
        myRevisions.add(ObjectId.fromString(revision));
      }
      myIncludeAll = false;
    }

    public RevisionsInfo() {
      myIncludeAll = true;
    }


    @NotNull
    Collection<ObjectId> getBranchRevisions(@NotNull Set<ObjectId> have) {
      if (myIncludeAll)
        return have;
      List<ObjectId> result = new ArrayList<ObjectId>();
      for (ObjectId id : myRevisions) {
        if (have.contains(id))
          result.add(id);
      }
      return result;
    }
  }


  @NotNull
  private RepositoryStateData excludeTags(@NotNull RepositoryStateData state) {
    String defaultBranch = state.getDefaultBranchName();
    Map<String, String> revisions = new HashMap<String, String>();
    for (Map.Entry<String, String> e : state.getBranchRevisions().entrySet()) {
      String ref = e.getKey();
      if (defaultBranch.equals(ref)) {
        revisions.put(ref, e.getValue());
      } else if (!GitUtils.isTag(ref)) {
        revisions.put(ref, e.getValue());
      }
    }
    return RepositoryStateData.createVersionState(defaultBranch, revisions);
  }


  private final static class PackHeuristicsFailed extends RuntimeException {
    public PackHeuristicsFailed(final String message) {
      super(message);
    }
    public PackHeuristicsFailed(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
