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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.execution.configurations.GeneralCommandLine;
import java.io.File;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitFacadeImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.ProcessTimeoutCallback;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class NativeGitFacade extends GitFacadeImpl implements AgentGitFacade {

  private final GitAgentSSHService mySsh;

  public NativeGitFacade(@NotNull GitAgentSSHService ssh,
                         @NotNull File repositoryDir,
                         @NotNull Context ctx) {
    super(repositoryDir, ctx);
    mySsh = ssh;
  }

  public NativeGitFacade(@NotNull String gitPath) {
    this(gitPath, new File("."));
  }

  public NativeGitFacade(@NotNull File repositoryDirh) {
    this("git", repositoryDirh);
  }

  public NativeGitFacade(@NotNull String gitPath,
                         @NotNull File repositoryDir) {
    super(repositoryDir, new NoBuildContext(gitPath));
    mySsh = null;
  }


  @NotNull
  public InitCommand init() {
    return new InitCommandImpl(createCommandLine());
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
  public UpdateRefCommand updateRef() {
    return new UpdateRefCommandImpl(createCommandLine());
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
  public GetConfigCommand getConfig() {
    return new GetConfigCommandImpl(createCommandLine());
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
  public VersionCommand version() {
    return new VersionCommandImpl(createCommandLine());
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
  public Branches listBranches(boolean all) throws VcsException {
    GitCommandLine cmd = createCommandLine();
    cmd.addParameter("branch");
    if (all) cmd.addParameter("-a");
    ExecResult r = CommandUtil.runCommand(cmd);
    CommandUtil.failIfNotEmptyStdErr(cmd, r);
    return parseBranches(r.getStdout());
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
      throw new VcsException("Error while resolving path " + f.getAbsolutePath(), e);
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
