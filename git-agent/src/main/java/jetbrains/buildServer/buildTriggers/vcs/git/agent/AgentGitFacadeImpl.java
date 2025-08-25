

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.execution.configurations.GeneralCommandLine;
import java.io.File;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.*;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class AgentGitFacadeImpl extends GitFacadeImpl implements AgentGitFacade {

  private final GitAgentSSHService mySsh;

  public AgentGitFacadeImpl(@NotNull GitAgentSSHService ssh,
                            @NotNull File repositoryDir,
                            @NotNull Context ctx) {
    super(repositoryDir, ctx);
    mySsh = ssh;
  }

  public AgentGitFacadeImpl(@NotNull String gitPath) {
    this(gitPath, new File("."));
  }

  public AgentGitFacadeImpl(@NotNull File repositoryDirh) {
    this("git", repositoryDirh);
  }

  public AgentGitFacadeImpl(@NotNull String gitPath,
                            @NotNull File repositoryDir) {
    super(repositoryDir, new StubContext(gitPath));
    mySsh = null;
  }


  @NotNull
  public InitCommand init() {
    return new InitCommandImpl(createCommandLine());
  }

  @NotNull
  public CloneCommand clone() {
    return new CloneCommandImpl(createCommandLine());
  }

  @NotNull
  public CreateBranchCommand createBranch() {
    return new CreateBranchCommandImpl(createCommandLine());
  }

  @NotNull
  public DeleteBranchCommand deleteBranch() {
    return new DeleteBranchCommandImpl(createCommandLine());
  }

  @NotNull
  public DeleteTagCommand deleteTag() {
    return new DeleteTagCommandImpl(createCommandLine());
  }

  @NotNull
  public AddRemoteCommand addRemote() {
    return new AddRemoteCommandImpl(createCommandLine());
  }

  @NotNull
  public CleanCommand clean() {
    return new CleanCommandImpl(createCommandLine());
  }

  @NotNull
  public ResetCommand reset() {
    return new ResetCommandImpl(createCommandLine());
  }

  @NotNull
  public UpdateRefBatchCommand updateRefBatch() {
    return new UpdateRefBatchCommandImpl(createCommandLine());
  }

  @NotNull
  public CheckoutCommand checkout() {
    return new CheckoutCommandImpl(createCommandLine());
  }

  @NotNull
  public GitConfigCommand gitConfig() {
    return new GitConfigCommandImpl(createCommandLine());
  }

  @NotNull
  public SetConfigCommand setConfig() {
    return new SetConfigCommandImpl(createCommandLine());
  }

  @NotNull
  @Override
  public ListConfigCommand listConfig() {
    return new ListConfigCommandImpl(createCommandLine());
  }

  @NotNull
  public LogCommand log() {
    return new LogCommandImpl(createCommandLine());
  }

  @NotNull
  public SubmoduleInitCommand submoduleInit() {
    return new SubmoduleInitCommandImpl(createCommandLine());
  }

  @NotNull
  public SubmoduleSyncCommand submoduleSync() {
    return new SubmoduleSyncCommandImpl(createCommandLine());
  }

  @NotNull
  public LsTreeCommand lsTree() {
    return new LsTreeCommandImpl(createCommandLine());
  }

  @NotNull
  public RevParseCommand revParse() {
    return new RevParseCommandImpl(createCommandLine());
  }

  @NotNull
  public SubmoduleUpdateCommand submoduleUpdate() {
    return new SubmoduleUpdateCommandImpl(createCommandLine());
  }

  @NotNull
  public ShowRefCommand showRef() {
    return new ShowRefCommandImpl(createCommandLine());
  }

  @NotNull
  public PackRefs packRefs() {
    return new PackRefsImpl(createCommandLine());
  }

  @NotNull
  public GcCommand gc() {
    return new GcCommandImpl(createCommandLine());
  }

  @NotNull
  public RepackCommand repack() {
    return new RepackCommandImpl(createCommandLine());
  }

  @NotNull
  @Override
  public UpdateIndexCommand updateIndex() {
    return new UpdateIndexCommandImpl(createCommandLine());
  }

  @NotNull
  @Override
  public DiffCommand diff() {
    return new DiffCommandImpl(createCommandLine());
  }

  @NotNull
  @Override
  public MergeCommand merge() {
    return new MergeCommandImpl(createCommandLine());
  }

  @NotNull
  @Override
  public FsckCommand fsck() {
    return new FsckCommandImpl(createCommandLine());
  }

  @Override
  public CountObjectsCommand countObjects() {
    return new CountObjectsCommandImpl(createCommandLine());
  }

  @NotNull
  public Branches listBranches(boolean all) throws VcsException {
    GitCommandLine cmd = createCommandLine();
    cmd.addParameter("branch");
    if (all) cmd.addParameter("-a");
    return parseBranches(CommandUtil.runCommand(cmd.stdErrExpected(false)).getStdout());
  }

  @NotNull
  public SetUpstreamCommand setUpstream(@NotNull String localBranch, @NotNull String upstreamBranch) throws VcsException {
    return new SetUpstreamCommandImpl(createCommandLine(), localBranch, upstreamBranch);
  }

  @NotNull
  public String resolvePath(@NotNull File f) throws VcsException {
    try {
      final GitExec gitExec = getCtx().getGitExec();
      if (gitExec.isCygwin()) {
        String cygwinBin = gitExec.getCygwinBinPath();
        GeneralCommandLine cmd = new GeneralCommandLine();
        cmd.setWorkDirectory(cygwinBin);
        cmd.setExePath(new File(cygwinBin, "cygpath.exe").getCanonicalPath());
        cmd.addParameter(f.getCanonicalPath());
        ExecResult res = SimpleCommandLineProcessRunner.runCommandSecure(cmd, cmd.getCommandLineString(), null, new ProcessTimeoutCallback(30));
        Throwable error = res.getException();
        if (error != null)
          throw error;
        return res.getStdout().trim();
      } else {
        return f.getCanonicalPath();
      }
    } catch (Throwable e) {
      throw new VcsException("Error while resolving path " + f.getAbsolutePath() + ": " + e.getMessage(), e);
    }
  }

  @NotNull
  protected AgentGitCommandLine createCommandLine() {
    return (AgentGitCommandLine)super.createCommandLine();
  }

  @NotNull
  @Override
  protected GitCommandLine makeCommandLine() {
    return new AgentGitCommandLine(mySsh, getScriptGen(), getCtx());
  }

  @NotNull
  private Branches parseBranches(String out) {
    Branches branches = new Branches();
    for (String l : StringUtil.splitByLines(out)) {
      String line = l.trim();
      if (isEmpty(line))
        continue;
      boolean currentBranch = line.startsWith("* ");
      String branchName = currentBranch ? line.substring(2).trim() : line;
      branches.addBranch(branchName, currentBranch);
    }
    return branches;
  }
}