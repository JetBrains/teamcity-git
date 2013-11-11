package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.isTag;

public class GitCommitsInfoBuilder implements CommitsInfoBuilder, GitServerExtension {

  private final GitVcsSupport myVcs;

  public GitCommitsInfoBuilder(@NotNull GitVcsSupport vcs) {
    myVcs = vcs;
    myVcs.addExtension(this);
  }

  @NotNull
  public List<CommitDataBean> collectCommits(@NotNull VcsRoot root,
                                             @NotNull CheckoutRules rules) throws VcsException {
    OperationContext ctx = myVcs.createContext(root, "collecting commits");
    try {
      GitVcsRoot gitRoot = makeRootWithTags(ctx, root);
      RepositoryStateData currentState = myVcs.getCurrentState(gitRoot);
      Repository db = ctx.getRepository();
      myVcs.getCollectChangesPolicy().ensureRepositoryStateLoadedFor(ctx, db, currentState);

      RevWalk walk = new RevWalk(db);
      Set<String> uniqueTips = new HashSet<String>(currentState.getBranchRevisions().values());
      for (String tip : uniqueTips) {
        try {
          RevObject obj = walk.parseAny(ObjectId.fromString(tip));
          if (obj instanceof RevCommit)
            walk.markStart((RevCommit) obj);
        } catch (MissingObjectException e) {
          //log
        } catch (IOException e) {
          //log
        }
      }

      Map<String, Set<String>> index = getCommitToRefIndex(currentState);
      List<CommitDataBean> commits = new ArrayList<CommitDataBean>();
      RevCommit c;
      while ((c = walk.next()) != null) {
        final CommitDataBean commit = new CommitDataBean(c.getId().getName(), c.getId().getName(), c.getAuthorIdent().getWhen());
        commit.setCommitAuthor(GitServerUtil.getUser(gitRoot, c));
        commit.setCommitMessage(c.getFullMessage());
        for (RevCommit p : c.getParents()) {
          commit.addParentRevision(p.getId().getName());
        }

        Set<String> refs = index.get(commit.getVersion());
        if (refs != null) {
          for (String ref : refs) {
            if (isTag(ref)) {
              commit.addTag(ref);
            } else {
              commit.addBranch(ref);
            }
          }
        }

        commits.add(commit);
      }
      return commits;
    } catch (Exception e) {
      throw new VcsException(e);
    } finally {
      ctx.close();
    }
  }


  @NotNull
  private GitVcsRoot makeRootWithTags(@NotNull OperationContext ctx, @NotNull VcsRoot root) throws VcsException {
    Map<String, String> params = new HashMap<String, String>(root.getProperties());
    params.put(Constants.REPORT_TAG_REVISIONS, "true");
    return ctx.getGitRoot(new VcsRootImpl(root.getId(), params));
  }


  @NotNull
  private Map<String, Set<String>> getCommitToRefIndex(@NotNull RepositoryStateData state) {
    Map<String, Set<String>> index = new HashMap<String, Set<String>>();
    for (Map.Entry<String, String> e : state.getBranchRevisions().entrySet()) {
      String ref = e.getKey();
      String commit = e.getValue();
      Set<String> refs = index.get(commit);
      if (refs == null) {
        refs = new HashSet<String>();
        index.put(commit, refs);
      }
      refs.add(ref);
    }
    return index;
  }

}
