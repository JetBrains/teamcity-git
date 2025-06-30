

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.io.File;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.DiffCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitFacade;
import jetbrains.buildServer.buildTriggers.vcs.git.command.InitCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.FsckCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.ListConfigCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public interface AgentGitFacade extends GitFacade {

  @NotNull
  InitCommand init();

  @NotNull
  CloneCommand clone();


  @NotNull
  CreateBranchCommand createBranch();

  @NotNull
  DeleteBranchCommand deleteBranch();

  @NotNull
  DeleteTagCommand deleteTag();

  @NotNull
  AddRemoteCommand addRemote();

  @NotNull
  CleanCommand clean();

  @NotNull
  ResetCommand reset();

  @NotNull
  UpdateRefBatchCommand updateRefBatch();

  @NotNull
  CheckoutCommand checkout();

  @NotNull
  ListConfigCommand listConfig();

  @NotNull
  LogCommand log();

  @NotNull
  LsTreeCommand lsTree();

  @NotNull
  RevParseCommand revParse();

  @NotNull
  SubmoduleInitCommand submoduleInit();

  SubmoduleSyncCommand submoduleSync();

  @NotNull
  SubmoduleUpdateCommand submoduleUpdate();

  @NotNull
  ShowRefCommand showRef();

  @NotNull
  PackRefs packRefs();

  @NotNull
  GcCommand gc();

  @NotNull
  RepackCommand repack();

  @NotNull
  Branches listBranches(boolean all) throws VcsException;

  @NotNull
  SetUpstreamCommand setUpstream(@NotNull String localBranch, @NotNull String upstreamBranch) throws VcsException;

  @NotNull
  String resolvePath(@NotNull File f) throws VcsException;

  @NotNull
  ScriptGen getScriptGen();

  @NotNull
  UpdateIndexCommand updateIndex();

  @NotNull
  DiffCommand diff();

  @NotNull
  MergeCommand merge();

  @NotNull
  FsckCommand fsck();
}