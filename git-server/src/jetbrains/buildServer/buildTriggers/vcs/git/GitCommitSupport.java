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
import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.vcs.CommitPatchBuilder;
import jetbrains.buildServer.vcs.CommitSupport;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushConnection;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;

public class GitCommitSupport implements CommitSupport, GitServerExtension {

  private static final Logger LOG = Logger.getInstance(GitCommitSupport.class.getName());

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final RepositoryManager myRepositoryManager;
  private final TransportFactory myTransportFactory;

  public GitCommitSupport(@NotNull GitVcsSupport vcs,
                          @NotNull CommitLoader commitLoader,
                          @NotNull RepositoryManager repositoryManager,
                          @NotNull TransportFactory transportFactory) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myRepositoryManager = repositoryManager;
    myTransportFactory = transportFactory;
    myVcs.addExtension(this);
  }

  @NotNull
  public CommitPatchBuilder getCommitPatchBuilder(@NotNull VcsRoot root) {
    OperationContext context = myVcs.createContext(root, "commit");
    try {
      Repository db = context.getRepository();
      return new GitCommitPatchBuilder(context, myCommitLoader, db, myRepositoryManager, myTransportFactory);
    } catch (VcsException e) {
      return new ErrorCommitPatchBuilder(e);
    }
  }


  private static class GitCommitPatchBuilder implements CommitPatchBuilder {
    private final OperationContext myContext;
    private final CommitLoader myCommitLoader;
    private final Repository myDb;
    private final ObjectInserter myObjectWriter;
    private final Map<String, ObjectId> myObjectMap = new HashMap<String, ObjectId>();
    private final RepositoryManager myRepositoryManager;
    private final TransportFactory myTransportFactory;


    private GitCommitPatchBuilder(@NotNull OperationContext context,
                                  @NotNull CommitLoader commitLoader,
                                  @NotNull Repository db,
                                  @NotNull RepositoryManager repositoryManager,
                                  @NotNull TransportFactory transportFactory) {
      myContext = context;
      myCommitLoader = commitLoader;
      myDb = db;
      myObjectWriter = db.newObjectInserter();
      myRepositoryManager = repositoryManager;
      myTransportFactory = transportFactory;
    }

    public void createFile(@NotNull String path, @NotNull InputStream content) {
      try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        FileUtil.copy(content, bytes);
        myObjectMap.put(path, myObjectWriter.insert(Constants.OBJ_BLOB, bytes.toByteArray()));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    public void deleteFile(@NotNull String path) {
      myObjectMap.put(path, ObjectId.zeroId());
    }

    public void commit(@NotNull String userName, @NotNull String description) throws VcsException {
      try {
        GitVcsRoot gitRoot = myContext.getGitRoot();
        RevCommit lastCommit = getLastCommit(gitRoot);
        ObjectId treeId = createNewTree(lastCommit);
        ObjectId commitId = createCommit(gitRoot, lastCommit, treeId, userName, description);

        synchronized (myRepositoryManager.getWriteLock(gitRoot.getRepositoryDir())) {
          final Transport tn = myTransportFactory.createTransport(myDb, gitRoot.getRepositoryPushURL(), gitRoot.getAuthSettings());
          try {
            final PushConnection c = tn.openPush();
            try {
              RemoteRefUpdate ru = new RemoteRefUpdate(myDb, null, commitId, GitUtils.expandRef(gitRoot.getRef()), false, null, lastCommit);
              c.push(NullProgressMonitor.INSTANCE, Collections.singletonMap(GitUtils.expandRef(gitRoot.getRef()), ru));
              switch (ru.getStatus()) {
                case UP_TO_DATE:
                case OK:
                  return;
                default:
                  throw new VcsException("Push failed");
              }
            } finally {
              c.close();
            }
          } catch (IOException e) {
            LOG.debug("Error while pushing a commit, root " + gitRoot + ", revision " + commitId + ", destination " + GitUtils.expandRef(gitRoot.getRef()), e);
            throw e;
          } finally {
            tn.close();
          }
        }
      } catch (Exception e) {
        throw myContext.wrapException(e);
      } finally {
        myContext.close();
      }
    }

    private ObjectId createCommit(@NotNull GitVcsRoot gitRoot,
                                  @NotNull RevCommit parentCommit,
                                  @NotNull ObjectId treeId,
                                  @NotNull String userName,
                                  @NotNull String description) throws IOException, VcsException {
      CommitBuilder commit = new CommitBuilder();
      commit.setTreeId(treeId);
      commit.setParentIds(parentCommit);
      commit.setCommitter(gitRoot.getTagger(myDb));
      switch (gitRoot.getUsernameStyle()) {
        case EMAIL:
          int idx = userName.indexOf("@");
          if (idx != -1) {
            commit.setAuthor(new PersonIdent(userName.substring(0, idx), userName));
          } else {
            commit.setAuthor(new PersonIdent(userName, userName));
          }
          break;
        case NAME:
          commit.setAuthor(new PersonIdent(userName, userName + "@acme.com"));
          break;
        case USERID:
          commit.setAuthor(new PersonIdent(userName, userName + "@acme.com"));
          break;
        case FULL:
          commit.setAuthor(gitRoot.parseIdent(userName));
          break;
      }
      commit.setMessage(description);
      ObjectId commitId = myObjectWriter.insert(commit);
      myObjectWriter.flush();
      return commitId;
    }

    @NotNull
    private ObjectId createNewTree(final RevCommit lastCommit) throws IOException {
      DirCache inCoreIndex = DirCache.newInCore();
      DirCacheBuilder tempBuilder = inCoreIndex.builder();
      for (Map.Entry<String, ObjectId> e : myObjectMap.entrySet()) {
        DirCacheEntry dcEntry = new DirCacheEntry(e.getKey());
        dcEntry.setObjectId(e.getValue());
        dcEntry.setFileMode(FileMode.REGULAR_FILE);
        tempBuilder.add(dcEntry);
      }

      TreeWalk treeWalk = new TreeWalk(myDb);
      treeWalk.addTree(lastCommit.getTree());
      while (treeWalk.next()) {
        String path = treeWalk.getPathString();
        ObjectId newObjectId = myObjectMap.get(path);
        if (newObjectId != null)
          continue;

        DirCacheEntry dcEntry = new DirCacheEntry(path);
        dcEntry.setFileMode(treeWalk.getFileMode(0));
        dcEntry.setObjectId(treeWalk.getObjectId(0));
        tempBuilder.add(dcEntry);
      }

      tempBuilder.finish();
      return inCoreIndex.writeTree(myObjectWriter);
    }

    @NotNull
    private RevCommit getLastCommit(final GitVcsRoot gitRoot) throws VcsException, IOException {
      RefSpec spec = new RefSpec().setSource(GitUtils.expandRef(gitRoot.getRef()))
        .setDestination(GitUtils.expandRef(gitRoot.getRef()))
        .setForceUpdate(true);
      myCommitLoader.fetch(myDb, gitRoot.getRepositoryFetchURL(), asList(spec), new FetchSettings(gitRoot.getAuthSettings()));
      Ref defaultBranch = myDb.getRef(GitUtils.expandRef(gitRoot.getRef()));
      return myCommitLoader.loadCommit(myContext, gitRoot, defaultBranch.getObjectId().name());
    }

    public void deleteDirectory(@NotNull final String path) {
    }

    public void createDirectory(@NotNull final String path) {
    }

    public void renameFile(@NotNull final String oldPath, @NotNull final String newPath, @NotNull InputStream content) {
      deleteFile(oldPath);
      createFile(newPath, content);
    }
  }

  private static class ErrorCommitPatchBuilder implements CommitPatchBuilder {
    private final VcsException myError;
    private ErrorCommitPatchBuilder(@NotNull VcsException error) {
      myError = error;
    }

    public void commit(@NotNull final String userName, @NotNull final String description) throws VcsException {
      throw myError;
    }

    public void createFile(@NotNull final String path, @NotNull final InputStream input) {
    }
    public void createDirectory(@NotNull final String path) {
    }
    public void deleteFile(@NotNull final String path) {
    }
    public void deleteDirectory(@NotNull final String path) {
    }
    public void renameFile(@NotNull final String oldPath, @NotNull final String newPath, @NotNull InputStream content) {
    }
  }
}
