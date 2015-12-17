/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

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
    OperationContext context = myVcs.createContext(root, "labelling");
    GitVcsRoot gitRoot = context.getGitRoot();
    if (myConfig.useTagPackHeuristics()) {
      LOG.debug("Update repository before labeling " + gitRoot.debugInfo());
      RepositoryStateData currentState = myVcs.getCurrentState(gitRoot);
      try {
        myVcs.getCollectChangesPolicy().ensureRepositoryStateLoadedFor(context, context.getRepository(), false, currentState);
      } catch (Exception e) {
        LOG.debug("Error while updating repository " + gitRoot.debugInfo(), e);
      }
    }
    ReadWriteLock rmLock = myRepositoryManager.getRmLock(gitRoot.getRepositoryDir());
    rmLock.readLock().lock();
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
      return push(label, version, gitRoot, r, tagRef);
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      rmLock.readLock().unlock();
      context.close();
    }
  }

  @NotNull
  private String push(@NotNull String label,
                      @NotNull String version,
                      @NotNull GitVcsRoot gitRoot,
                      @NotNull Repository r,
                      @NotNull Ref tagRef) throws VcsException, IOException {
    long pushStart = System.currentTimeMillis();
    final Transport tn = myTransportFactory.createTransport(r, gitRoot.getRepositoryPushURL(), gitRoot.getAuthSettings());
    PushConnection c = null;
    try {
      c = tn.openPush();
      RemoteRefUpdate ru = new RemoteRefUpdate(r, tagRef.getName(), tagRef.getObjectId(), tagRef.getName(), false, null, null);
      if (c instanceof BasePackPushConnection) {
        final RevTag tagObject = getTagObject(r, tagRef);
        if (tagObject != null) {
          ((BasePackPushConnection)c).setPreparePack(new PreparePackFunction(tagObject));
        } else {
          LOG.debug("Cannot locate the " + tagRef.getName() + " tag object, don't use pack heuristic");
        }
      }
      c.push(NullProgressMonitor.INSTANCE, Collections.singletonMap(tagRef.getName(), ru));
      LOG.info("Tag  " + label + "=" + version + " was pushed with status " + ru.getStatus() + " for " + gitRoot.debugInfo() +
               " in " + (System.currentTimeMillis() - pushStart) + "ms");
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

    public PreparePackFunction(@NotNull RevTag tagObject) {
      myTagObject = tagObject;
    }

    public void preparePack(ProgressMonitor monitor,
                            Repository repository,
                            PackWriter writer,
                            Set<ObjectId> want,
                            Set<ObjectId> have) throws IOException {
      boolean writeOnlyTag = false;
      if (myConfig.useTagPackHeuristics()) {
        RevWalk walk = new RevWalk(repository);
        try {
          RevObject taggedObject = walk.parseAny(myTagObject.getObject());
          if (taggedObject.getType() == org.eclipse.jgit.lib.Constants.OBJ_COMMIT) {
            RevCommit taggedCommit = walk.parseCommit(taggedObject);
            writeOnlyTag = remoteRepositoryContainsCommit(walk, taggedCommit, have);
            if (!writeOnlyTag) {
              LOG.debug("Remote repository doesn't contain the tagged object " + myTagObject.getObject() +
                        ", use default prepare pack logic");
            }
          }
        } catch (Exception e) {
          LOG.debug("Failed to determine if the tagged object " + myTagObject.getObject() +
                    " is present in the remote repository, use default prepare pack logic");
        } finally {
          walk.release();
        }
      }

      if (writeOnlyTag) {
        writer.preparePack(asList((RevObject)myTagObject).iterator());
      } else {
        writer.preparePack(monitor, want, have);
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
      for (ObjectId tip : have) {
        RevCommit tipCommit;
        try {
          tipCommit = walk.parseCommit(tip);
        } catch (Exception e) {
          continue;
        }
        try {
          walk.markStart(tipCommit);
          while ((c = walk.next()) != null) {
            if (c.equals(commit))
              return true;
          }
        } catch (Exception e) {
          //ignore
        }
      }

      return false;
    }
  }
}
