package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.vcs.MergeOptions;
import jetbrains.buildServer.vcs.MergeSupport;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.jetbrains.annotations.NotNull;

public class InternalGitBranchSupport {
  private final GitVcsSupport myVcs;
  private final TransportFactory myTransportFactory;
  private final CommitLoader myCommitLoader;

  public InternalGitBranchSupport(@NotNull GitVcsSupport vcs,
                                  @NotNull TransportFactory transportFactory,
                                  @NotNull CommitLoader commitLoader) {
    myVcs = vcs;
    myTransportFactory = transportFactory;
    myCommitLoader = commitLoader;
  }

  public String createBranchProto(@NotNull VcsRoot root, @NotNull String srcBranch) throws VcsException {
    OperationContext context = myVcs.createContext(root, "branchCreation");
    Repository db = context.getRepository();
    System.out.println("REPOSITORY: " + db.toString());

    Git git = new Git(db);
    try {
      String newBranchName = "check_create_" + System.currentTimeMillis();
      git.branchCreate()
         .setName(newBranchName)
         .setStartPoint(srcBranch)
         .call();


      System.out.println("branch " + newBranchName + " was created");
      System.out.println("TODO push functionality");

      GitVcsRoot gitRoot = context.getGitRoot();

      try {

        final Transport tn = myTransportFactory.createTransport(db, gitRoot.getRepositoryPushURL().get(), gitRoot.getAuthSettings(),
                                                                10);

        String topNewBranchCommitRevision = git.log().add(db.resolve(newBranchName)).call().iterator().next().getName();

        System.out.println("top commit: " + topNewBranchCommitRevision);

        ObjectId commitId = myCommitLoader.loadCommit(context, gitRoot, topNewBranchCommitRevision);

        RemoteRefUpdate ru = new RemoteRefUpdate(db,
                                                 null,
                                                 commitId,
                                                 GitUtils.expandRef(newBranchName), //todo
                                                 false,
                                                 "refs/heads/" + newBranchName,
                                                 null);

        tn.push(NullProgressMonitor.INSTANCE, Collections.singletonList(ru)); //todo close

        System.out.println("pushed " + ru.getStatus() + " " + ru.getMessage());

      } catch (TransportException | NotSupportedException ex) {
        ex.printStackTrace();
      } catch (AmbiguousObjectException e) {
        e.printStackTrace();
      } catch (IncorrectObjectTypeException e) {
        e.printStackTrace();
      } catch (MissingObjectException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }

      return newBranchName;
    } catch (GitAPIException jgitException) {
      throw new VcsException(jgitException);
    }
  }

  public void PrintBranches(Git git) throws GitAPIException {
    List<Ref> call = git.branchList().call();
    for (Ref ref : call) {
      System.out.println("Branch: " + ref + " " + ref.getName() + " " + ref.getObjectId().getName());
    }

    System.out.println("Now including remote branches:");
    call = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
    for (Ref ref : call) {
      System.out.println("Branch: " + ref + " " + ref.getName() + " " + ref.getObjectId().getName());
    }
  }

  public void merge(@NotNull VcsRoot root,
                    @NotNull String src,
                    @NotNull String dst,
                    @NotNull MergeSupport mergeSupport) throws VcsException {
    mergeSupport.merge(root, src, dst, "preliminary merge commit", new MergeOptions());
  }
}
