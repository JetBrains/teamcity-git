/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author dmitry.neverov
 */
public class NativeGitFacade implements GitFacade {

  private final GitAgentSSHService mySsh;
  private final AskPassGenerator myAskPassGen;
  private final String myGitPath;
  private final File myRepositoryDir;
  private final boolean myDeleteTempFiles;

  public NativeGitFacade(@NotNull GitAgentSSHService ssh,
                         @NotNull String gitPath,
                         @NotNull File repositoryDir,
                         boolean deleteTempFiles) {
    mySsh = ssh;
    myAskPassGen = makeAskPassGen();
    myGitPath = gitPath;
    myRepositoryDir = repositoryDir;
    myDeleteTempFiles = deleteTempFiles;
  }

  public NativeGitFacade(@NotNull String gitPath) {
    mySsh = null;
    myAskPassGen = makeAskPassGen();
    myGitPath = gitPath;
    myRepositoryDir = new File(".");
    myDeleteTempFiles = true;
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
  public CheckoutCommand checkout() {
    return new CheckoutCommandImpl(createCommandLine());
  }

  @NotNull
  public BranchCommand branch() {
    return new BranchCommandImpl(createCommandLine());
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
  public FetchCommand fetch() {
    return new FetchCommandImpl(createCommandLine());
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
  public LsRemoteCommand lsRemote() {
    return new LsRemoteCommandImpl(createCommandLine());
  }

  @NotNull
  private GitCommandLine createCommandLine() {
    GitCommandLine cmd = new GitCommandLine(mySsh, myAskPassGen, myDeleteTempFiles);
    cmd.setExePath(myGitPath);
    cmd.setWorkingDirectory(myRepositoryDir);
    return cmd;
  }

  @NotNull
  private AskPassGenerator makeAskPassGen() {
    return SystemInfo.isUnix ? new UnixAskPassGen(new EscapeEchoArgumentUnix()) : new WinAskPassGen(new EscapeEchoArgumentWin());
  }

}
