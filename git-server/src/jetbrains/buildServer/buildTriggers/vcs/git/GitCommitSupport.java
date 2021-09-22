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
import com.intellij.openapi.util.io.FileUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.AutoLFInputStream;
import org.jetbrains.annotations.NotNull;

import static java.util.Arrays.asList;

public class GitCommitSupport implements CommitSupport, GitServerExtension {

  private static final Logger LOG = Logger.getInstance(GitCommitSupport.class.getName());

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final RepositoryManager myRepositoryManager;
  private final GitRepoOperations myRepoOperations;

  public GitCommitSupport(@NotNull GitVcsSupport vcs,
                          @NotNull CommitLoader commitLoader,
                          @NotNull RepositoryManager repositoryManager,
                          @NotNull GitRepoOperations repoOperations) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myRepositoryManager = repositoryManager;
    myRepoOperations = repoOperations;
    myVcs.addExtension(this);
  }

  @NotNull
  public CommitPatchBuilder getCommitPatchBuilder(@NotNull VcsRoot root) throws VcsException {
    OperationContext context = myVcs.createContext(root, "commit");
    Lock rmLock = myRepositoryManager.getRmLock(context.getGitRoot().getRepositoryDir()).readLock();
    rmLock.lock();
    Repository db = context.getRepository();
    return new GitCommitPatchBuilder(myVcs, context, myCommitLoader, db, myRepositoryManager, myRepoOperations, rmLock);
  }


  private static class GitCommitPatchBuilder implements CommitPatchBuilder {
    private final GitVcsSupport myVcs;
    private final OperationContext myContext;
    private final CommitLoader myCommitLoader;
    private final Repository myDb;
    private final ObjectInserter myObjectWriter;
    private final Map<String, ObjectId> myObjectMap = new HashMap<String, ObjectId>();
    private final Set<String> myDeletedDirs = new HashSet<String>();
    private final RepositoryManager myRepositoryManager;
    private final GitRepoOperations myRepoOperations;
    private final Lock myRmLock;

    private GitCommitPatchBuilder(@NotNull GitVcsSupport vcs,
                                  @NotNull OperationContext context,
                                  @NotNull CommitLoader commitLoader,
                                  @NotNull Repository db,
                                  @NotNull RepositoryManager repositoryManager,
                                  @NotNull GitRepoOperations repoOperations,
                                  @NotNull Lock rmLock) {
      myVcs = vcs;
      myContext = context;
      myCommitLoader = commitLoader;
      myDb = db;
      myObjectWriter = db.newObjectInserter();
      myRepositoryManager = repositoryManager;
      myRepoOperations = repoOperations;
      myRmLock = rmLock;
    }

    public void createFile(@NotNull String path, @NotNull InputStream content) throws VcsException {
      try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        FileUtil.copy(content, bytes);
        //taken from WorkingTreeIterator
        AutoLFInputStream eolStream = new AutoLFInputStream(new ByteArrayInputStream(bytes.toByteArray()), true, true);
        long length;
        try {
          length = computeLength(eolStream);
        } catch (AutoLFInputStream.IsBinaryException e) {
          //binary file, insert as is:
          myObjectMap.put(path, myObjectWriter.insert(Constants.OBJ_BLOB, bytes.toByteArray()));
          return;
        } finally {
          eolStream.close();
        }
        eolStream = new AutoLFInputStream(new ByteArrayInputStream(bytes.toByteArray()), true, true);
        myObjectMap.put(path, myObjectWriter.insert(Constants.OBJ_BLOB, length, eolStream));
      } catch (IOException e) {
        throw new VcsException("Error while inserting file content to repository, file: " + path, e);
      }
    }


    private long computeLength(InputStream in) throws IOException {
      // Since we only care about the length, use skip. The stream
      // may be able to more efficiently wade through its data.
      //
      long length = 0;
      for (;;) {
        long n = in.skip(1 << 20);
        if (n <= 0)
          break;
        length += n;
      }
      return length;
    }

    public void deleteFile(@NotNull String path) {
      myObjectMap.put(path, ObjectId.zeroId());
    }

    @NotNull
    public CommitResult commit(@NotNull CommitSettings commitSettings) throws VcsException {
      try {
        LOG.info("Committing change '" + commitSettings.getDescription() + "'");
        GitVcsRoot gitRoot = myContext.getGitRoot();
        RevCommit lastCommit = getLastCommit(gitRoot);
        LOG.info("Parent commit " + lastCommit.name());
        ObjectId treeId = createNewTree(lastCommit);
        if (!ObjectId.zeroId().equals(lastCommit.getId()) && lastCommit.getTree().getId().equals(treeId))
          return CommitResult.createRepositoryUpToDateResult(lastCommit.getId().name());

        ObjectId commitId = createCommit(gitRoot, lastCommit, treeId, commitSettings.getUserName(), nonEmptyMessage(commitSettings));

        ReentrantLock lock = myRepositoryManager.getWriteLock(gitRoot.getRepositoryDir());
        lock.lock();
        try {
          final CommitResult result =
            myRepoOperations.pushCommand(gitRoot.getRepositoryPushURL().toString()).push(myDb, gitRoot, gitRoot.getRef(), commitId.name(), lastCommit.name());
          Loggers.VCS.info("Change '" + commitSettings.getDescription() + "' was successfully committed");
          return result;
        } finally {
          lock.unlock();
        }
      } catch (Exception e) {
        throw myContext.wrapException(e);
      }
    }

    @NotNull
    private String nonEmptyMessage(@NotNull CommitSettings commitSettings) {
      String msg = commitSettings.getDescription();
      if (!StringUtil.isEmpty(msg))
        return msg;
      return "no comments";
    }

    private ObjectId createCommit(@NotNull GitVcsRoot gitRoot,
                                  @NotNull RevCommit parentCommit,
                                  @NotNull ObjectId treeId,
                                  @NotNull String userName,
                                  @NotNull String description) throws IOException, VcsException {
      CommitBuilder commit = new CommitBuilder();
      commit.setTreeId(treeId);
      if (!ObjectId.zeroId().equals(parentCommit.getId()))
        commit.setParentIds(parentCommit);
      commit.setCommitter(PersonIdentFactory.getTagger(gitRoot, myDb));
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
          commit.setAuthor(new PersonIdent(userName, userName + "@TeamCity"));
          break;
        case USERID:
          commit.setAuthor(new PersonIdent(userName, userName + "@TeamCity"));
          break;
        case FULL:
          commit.setAuthor(PersonIdentFactory.parseIdent(userName));
          break;
      }
      commit.setMessage(description);
      ObjectId commitId = myObjectWriter.insert(commit);
      myObjectWriter.flush();
      return commitId;
    }

    @NotNull
    private ObjectId createNewTree(@NotNull RevCommit lastCommit) throws IOException {
      DirCache inCoreIndex = DirCache.newInCore();
      DirCacheBuilder tempBuilder = inCoreIndex.builder();
      for (Map.Entry<String, ObjectId> e : myObjectMap.entrySet()) {
        if (!ObjectId.zeroId().equals(e.getValue())) {
          DirCacheEntry dcEntry = new DirCacheEntry(e.getKey());
          dcEntry.setObjectId(e.getValue());
          dcEntry.setFileMode(FileMode.REGULAR_FILE);
          tempBuilder.add(dcEntry);
        }
      }

      TreeWalk treeWalk = new TreeWalk(myDb);
      if (!ObjectId.zeroId().equals(lastCommit.getId())) {
        treeWalk.addTree(lastCommit.getTree());
        treeWalk.setRecursive(true);
        while (treeWalk.next()) {
          String path = treeWalk.getPathString();
          ObjectId newObjectId = myObjectMap.get(path);
          if (newObjectId != null)
            continue;

          boolean deleted = false;
          for (String dir : myDeletedDirs) {
            if (path.startsWith(dir)) {
              deleted = true;
              break;
            }
          }
          if (deleted)
            continue;

          DirCacheEntry dcEntry = new DirCacheEntry(path);
          dcEntry.setFileMode(treeWalk.getFileMode(0));
          dcEntry.setObjectId(treeWalk.getObjectId(0));
          tempBuilder.add(dcEntry);
        }
      }
      tempBuilder.finish();
      return inCoreIndex.writeTree(myObjectWriter);
    }

    @NotNull
    private RevCommit getLastCommit(@NotNull GitVcsRoot gitRoot) throws VcsException, IOException {
      Map<String, Ref> refs = myVcs.getRemoteRefs(gitRoot.getOriginalRoot());
      Ref ref = refs.get(GitUtils.expandRef(gitRoot.getRef()));
      if (!refs.isEmpty() && ref == null)
        throw new VcsException("The '" + gitRoot.getRef() + "' destination branch doesn't exist");
      RevWalk revWalk = new RevWalk(myDb);
      try {
        if (ref == null)
          return revWalk.lookupCommit(ObjectId.zeroId());
        return revWalk.parseCommit(ref.getObjectId());
      } catch (Exception e) {
        //will try to fetch
      } finally {
        revWalk.close();
      }
      RefSpec spec = new RefSpec().setSource(GitUtils.expandRef(gitRoot.getRef()))
        .setDestination(GitUtils.expandRef(gitRoot.getRef()))
        .setForceUpdate(true);
      myCommitLoader.fetch(myDb, gitRoot.getRepositoryFetchURL().get(), asList(spec), new FetchSettings(gitRoot.getAuthSettings()));
      Ref defaultBranch = myDb.exactRef(GitUtils.expandRef(gitRoot.getRef()));
      return myCommitLoader.loadCommit(myContext, gitRoot, defaultBranch.getObjectId().name());
    }

    public void deleteDirectory(@NotNull final String path) {
      String dirPath = path.endsWith("/") ? path : path + "/";
      myDeletedDirs.add(dirPath);
    }

    public void createDirectory(@NotNull final String path) {
    }

    public void renameFile(@NotNull final String oldPath, @NotNull final String newPath, @NotNull InputStream content) throws VcsException {
      deleteFile(oldPath);
      createFile(newPath, content);
    }

    public void dispose() {
      myRmLock.unlock();
      myContext.close();
    }
  }
}
