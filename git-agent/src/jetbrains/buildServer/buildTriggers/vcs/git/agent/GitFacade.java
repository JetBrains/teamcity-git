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

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author dmitry.neverov
 */
public interface GitFacade {

  @NotNull
  InitCommand init();

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
  UpdateRefCommand updateRef();

  @NotNull
  UpdateRefBatchCommand updateRefBatch();

  @NotNull
  CheckoutCommand checkout();

  @NotNull
  GetConfigCommand getConfig();

  @NotNull
  SetConfigCommand setConfig();

  @NotNull
  FetchCommand fetch();

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
  VersionCommand version();

  @NotNull
  LsRemoteCommand lsRemote();

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
}
