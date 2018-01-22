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
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushConnection;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyNotSupportedException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyTransportException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.isAnonymousGitWithUsername;

/**
 * @author dmitry.neverov
 */
public class TestConnectionCommand {

  private static Logger LOG = Logger.getInstance(TestConnectionCommand.class.getName());

  private final GitVcsSupport myGit;
  private final TransportFactory myTransportFactory;
  private final RepositoryManager myRepositoryManager;

  public TestConnectionCommand(@NotNull GitVcsSupport git,
                               @NotNull TransportFactory transportFactory,
                               @NotNull RepositoryManager repositoryManager) {
    myGit = git;
    myTransportFactory = transportFactory;
    myRepositoryManager = repositoryManager;
  }


  public String testConnection(@NotNull OperationContext context) throws Exception {
    GitVcsRoot root = context.getGitRoot();
    File repositoryTempDir = null;
    try {
      repositoryTempDir = FileUtil.createTempDirectory("git-testcon", "");
      root.setCustomRepositoryDir(repositoryTempDir);
      Repository r = context.getRepository();
      try {
        if (LOG.isDebugEnabled())
          LOG.debug("Opening connection for " + root.debugInfo());
        validateBranchSpec(root);
        checkFetchConnection(root);
        checkPushConnection(root, r);
        return null;
      } catch (NotSupportedException nse) {
        throw friendlyNotSupportedException(root, nse);
      } catch (TransportException te) {
        throw friendlyTransportException(te, root);
      }
    } finally {
      if (repositoryTempDir != null) {
        myRepositoryManager.cleanLocksFor(repositoryTempDir);
        FileUtil.delete(repositoryTempDir);
      }
    }
  }

  private void validateBranchSpec(@NotNull GitVcsRoot root) throws VcsException {
    String specStr = root.getBranchSpec();
    VcsPropertiesProcessor validator = new VcsPropertiesProcessor();
    InvalidProperty error = validator.validateBranchSpec(specStr);
    if (error != null)
      throw new VcsException("Branch specification error: " + error.getInvalidReason());
  }


  private void checkFetchConnection(@NotNull GitVcsRoot root) throws NotSupportedException, VcsException, TransportException {
    validate(root.getRepositoryFetchURLNoFixedErrors());
    myGit.getCurrentState(root);
  }


  private void checkPushConnection(GitVcsRoot root, Repository r) throws NotSupportedException, VcsException, TransportException {
    if (!root.getRepositoryFetchURLNoFixedErrors().equals(root.getRepositoryPushURLNoFixedErrors())) {
      validate(root.getRepositoryPushURLNoFixedErrors());
      final Transport push = myTransportFactory.createTransport(r, root.getRepositoryPushURLNoFixedErrors(), root.getAuthSettings());
      PushConnection c = null;
      try {
        c = push.openPush();
        c.getRefs();
      } finally {
        if (c != null) {
          c.close();
        }
        push.close();
      }
    }
  }


  /**
   * Check that if url use anonymous git scheme it does not contain a username,
   * because a native git throws an error in this case. It seems like native git
   * thinks 'username@' is a part of the host name and cannot resolve such a host.
   * Native git error is: "Unable to look up git@git.labs.intellij.net (port 9418) (A non-recoverable error occurred during a database lookup.)"
   *
   * Described url works fine with server-side checkout (jgit ignores a username),
   * but same root could be used with agent-side checkout so it is better to fail
   * during test connection.
   *
   * @param uri url to check
   * @throws VcsException if url use anonymous git protocol and contains username
   */
  private void validate(URIish uri) throws VcsException {
    if (isAnonymousGitWithUsername(uri))
      throw new VcsException("Incorrect url " + uri.toString() + ": anonymous git url should not contain a username");
  }
}
