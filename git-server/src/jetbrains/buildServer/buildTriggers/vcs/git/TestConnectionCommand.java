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

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchConnection;
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

  private final TransportFactory myTransportFactory;
  private final RepositoryManager myRepositoryManager;


  public TestConnectionCommand(TransportFactory transportFactory, RepositoryManager repositoryManager) {
    myTransportFactory = transportFactory;
    myRepositoryManager = repositoryManager;
  }


  public String testConnection(@NotNull OperationContext context) throws Exception {
    GitVcsRoot root = context.getGitRoot();
    File repositoryTempDir = null;
    try {
      repositoryTempDir = FileUtil.createTempDirectory("git-testcon", "");
      root.setUserDefinedRepositoryPath(repositoryTempDir);
      Repository r = context.getRepository();
      try {
        if (LOG.isDebugEnabled())
          LOG.debug("Opening connection for " + root.debugInfo());
        checkFetchConnection(root, r);
        checkPushConnection(root, r);
        return null;
      } catch (NotSupportedException nse) {
        throw friendlyNotSupportedException(root, nse);
      } catch (TransportException te) {
        throw friendlyTransportException(te);
      }
    } finally {
      if (repositoryTempDir != null) {
        myRepositoryManager.cleanLocksFor(repositoryTempDir);
        FileUtil.delete(repositoryTempDir);
      }
    }
  }


  private void checkFetchConnection(GitVcsRoot root, Repository r) throws NotSupportedException, VcsException, TransportException {
    validate(root.getRepositoryFetchURLNoFixedErrors());
    final Transport tn = myTransportFactory.createTransport(r, root.getRepositoryFetchURLNoFixedErrors(), root.getAuthSettings());
    FetchConnection c = null;
    try {
      c = tn.openFetch();
      if (LOG.isDebugEnabled())
        LOG.debug("Checking references... " + root.debugInfo());
      checkRefExists(root, c);
    } finally {
      if (c != null)
        c.close();
      tn.close();
    }
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
        if (c != null)
          c.close();
        push.close();
      }
    }
  }


  private void checkRefExists(@NotNull GitVcsRoot root, @NotNull FetchConnection c) throws VcsException {
    String refName = GitUtils.expandRef(root.getRef());
    if (!isRefExist(root, c, refName))
      throw new VcsException("The ref '" + refName + "' was not found in the repository " + root.getRepositoryFetchURL().toString());
  }


  private boolean isRefExist(@NotNull GitVcsRoot root, @NotNull FetchConnection c, @NotNull String refName) {
    for (final Ref ref : c.getRefs()) {
      if (refName.equals(ref.getName())) {
        LOG.info("Found the ref " + refName + "=" + ref.getObjectId() + " for " + root.debugInfo());
        return true;
      }
    }
    return false;
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
