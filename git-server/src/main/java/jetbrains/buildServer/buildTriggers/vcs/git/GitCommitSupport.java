

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
import jetbrains.buildServer.serverSide.TeamCityProperties;
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
import org.jetbrains.annotations.Nullable;

import static java.util.Arrays.asList;

public class GitCommitSupport implements CommitSupport, GitServerExtension {

  private static final Logger LOG = Logger.getInstance(GitCommitSupport.class.getName());

  /**
   * When committing to an explicit target branch that does not yet exist on the remote, the
   * plugin parents the new commit on the live tip of the VCS root's configured ref and pushes
   * the result as a branch creation. Disable to restore the legacy "branch must already exist"
   * behaviour.
   */
  private static final String CREATE_TARGET_BRANCH_ON_DEMAND = "teamcity.git.commit.createTargetBranchOnDemand";

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
    return getCommitPatchBuilderInternal(root, null);
  }

  @NotNull
  @Override
  public CommitPatchBuilder getCommitPatchBuilder(@NotNull VcsRoot root, @NotNull String targetBranch) throws VcsException {
    return getCommitPatchBuilderInternal(root, targetBranch);
  }

  @NotNull
  private CommitPatchBuilder getCommitPatchBuilderInternal(@NotNull VcsRoot root,
                                                           @Nullable String targetBranch) throws VcsException {
    OperationContext context = myVcs.createContext(root, "commit");
    Lock rmLock = myRepositoryManager.getRmLock(context.getGitRoot().getRepositoryDir()).readLock();
    rmLock.lock();
    Repository db = context.getRepository();
    return new GitCommitPatchBuilder(myVcs, context, myCommitLoader, db, myRepositoryManager, myRepoOperations, rmLock, targetBranch);
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
    @Nullable private final String myTargetRef;
    // Set in getLastCommit when we discover the target ref is absent on the remote and we create
    // it from the VCS root's configured ref. The push then uses zero-id as the expected old tip
    // (RemoteRefUpdate / git update-ref semantics for "ref must not exist").
    private boolean myCreatingNewBranch;

    private GitCommitPatchBuilder(@NotNull GitVcsSupport vcs,
                                  @NotNull OperationContext context,
                                  @NotNull CommitLoader commitLoader,
                                  @NotNull Repository db,
                                  @NotNull RepositoryManager repositoryManager,
                                  @NotNull GitRepoOperations repoOperations,
                                  @NotNull Lock rmLock,
                                  @Nullable String targetRef) {
      myVcs = vcs;
      myContext = context;
      myCommitLoader = commitLoader;
      myDb = db;
      myObjectWriter = db.newObjectInserter();
      myRepositoryManager = repositoryManager;
      myRepoOperations = repoOperations;
      myRmLock = rmLock;
      myTargetRef = targetRef;
    }

    @NotNull
    private String effectiveRef(@NotNull GitVcsRoot gitRoot) {
      return myTargetRef != null ? myTargetRef : gitRoot.getRef();
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
        throw new VcsException("Error while inserting file content to repository, file: " + path + ": " + e.getMessage(), e);
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
          // For brand-new branch creation, the push must assert "ref does not exist" via zero-id;
          // otherwise we send the current tip's id so the push is a fast-forward.
          String expectedOldTip = myCreatingNewBranch ? ObjectId.zeroId().name() : lastCommit.name();
          final CommitResult result =
            myRepoOperations.pushCommand(gitRoot.getRepositoryPushURL().toString()).push(myDb, gitRoot, effectiveRef(gitRoot), commitId.name(), expectedOldTip);
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
                                  @NotNull String description) throws IOException {
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
      String targetRef = effectiveRef(gitRoot);
      Map<String, Ref> refs = myVcs.getRemoteRefs(gitRoot.getOriginalRoot());
      Ref ref = refs.get(GitUtils.expandRef(targetRef));
      if (!refs.isEmpty() && ref == null) {
        Ref baseRef = resolveAutoCreateBaseRef(gitRoot, refs);
        if (baseRef != null) {
          // Target branch missing on a populated remote: parent the new commit on the live tip
          // of the VCS root's configured ref. push() will use zero-id as the expected previous tip.
          myCreatingNewBranch = true;
          return fetchAndLoadCommit(gitRoot, baseRef.getName());
        }
        throw new VcsException("The '" + targetRef + "' destination branch doesn't exist");
      }
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
      return fetchAndLoadCommit(gitRoot, GitUtils.expandRef(targetRef));
    }

    /**
     * When the caller specified an explicit target branch and it is missing on the remote,
     * resolve the VCS root's configured ref so the new branch can be parented on its live tip.
     * Returns {@code null} when auto-creation is disabled, no explicit target branch was
     * requested, or the configured ref itself is also missing on the remote.
     */
    @Nullable
    private Ref resolveAutoCreateBaseRef(@NotNull GitVcsRoot gitRoot, @NotNull Map<String, Ref> refs) {
      if (myTargetRef == null) return null;
      if (!TeamCityProperties.getBooleanOrTrue(CREATE_TARGET_BRANCH_ON_DEMAND)) return null;
      return refs.get(GitUtils.expandRef(gitRoot.getRef()));
    }

    @NotNull
    private RevCommit fetchAndLoadCommit(@NotNull GitVcsRoot gitRoot, @NotNull String expandedRefName)
      throws VcsException, IOException {
      RefSpec spec = new RefSpec().setSource(expandedRefName).setDestination(expandedRefName).setForceUpdate(true);
      myCommitLoader.fetch(myDb, gitRoot.getRepositoryFetchURL().get(),
                           new FetchSettings(gitRoot.getAuthSettings(), asList(spec)));
      Ref fetched = myDb.exactRef(expandedRefName);
      if (fetched == null) {
        throw new VcsException("Ref " + expandedRefName + " not found in remote " + gitRoot.getRepositoryFetchURL());
      }
      return myCommitLoader.loadCommit(myContext, gitRoot, fetched.getObjectId().name());
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