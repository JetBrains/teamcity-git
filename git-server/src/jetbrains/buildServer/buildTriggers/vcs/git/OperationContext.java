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
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleResolverImpl;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;

import static jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy.getPolicyWithErrorsIgnored;
import static jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleAwareTreeIteratorFactory.create;

/**
 *
 */
public class OperationContext {

  private static Logger LOG = Logger.getInstance(OperationContext.class.getName());

  private final CommitLoader myCommitLoader;
  private final RepositoryManager myRepositoryManager;
  private final VcsRoot myRoot;
  private final String myOperation;
  private final Map<String, Repository> myRepositories = new HashMap<String, Repository>(); //repository path -> repository
  private final Set<String> myAlreadyFetched = new HashSet<String>();
  private final GitProgress myProgress;
  private final ServerPluginConfig myPluginConfig;
  private final Map<String, StoredConfig> myConfigsCache = new HashMap<String, StoredConfig>(); //repository path -> its config

  public OperationContext(@NotNull final CommitLoader commitLoader,
                          @NotNull final RepositoryManager repositoryManager,
                          @Nullable final VcsRoot root,
                          @NotNull final String operation,
                          @NotNull final GitProgress progress,
                          @NotNull final ServerPluginConfig pluginConfig) {
    myCommitLoader = commitLoader;
    myRepositoryManager = repositoryManager;
    myRoot = root;
    myOperation = operation;
    myProgress = progress;
    myPluginConfig = pluginConfig;
  }


  public VcsRoot getRoot() {
    return myRoot;
  }

  public Repository getRepository() throws VcsException {
    return getRepository(getGitRoot());
  }

  public Repository getRepository(GitVcsRoot settings) throws VcsException {
    return getRepository(settings.getRepositoryDir(), settings.getRepositoryFetchURL());
  }

  public Repository getRepository(File repositoryDir, URIish fetchUrl) throws VcsException {
    Repository result = myRepositories.get(repositoryDir.getPath());
    if (result == null) {
      result = myRepositoryManager.openRepository(repositoryDir, fetchUrl);
      myRepositories.put(repositoryDir.getPath(), result);
    }
    return result;
  }

  @NotNull
  public File getRepositoryDir(@NotNull URIish uri) {
    return myRepositoryManager.getMirrorDir(uri.toString());
  }

  @NotNull
  public Repository getRepositoryFor(@NotNull final URIish uri) throws VcsException {
    File dir = myRepositoryManager.getMirrorDir(uri.toString());
    Repository result = myRepositories.get(dir.getPath());
    if (result != null)
      return result;
    return getRepository(dir, uri);
  }

  public GitVcsRoot getGitRoot() throws VcsException {
    return getGitRoot(myRoot);
  }

  public GitVcsRoot getGitRoot(@NotNull VcsRoot root) throws VcsException {
    return new GitVcsRoot(myRepositoryManager, root);
  }

  @NotNull
  public StoredConfig getConfig(@NotNull Repository r) {
    String repositoryPath = r.getDirectory().getAbsolutePath();
    StoredConfig result = myConfigsCache.get(repositoryPath);
    if (result == null) {
      result = r.getConfig();
      myConfigsCache.put(repositoryPath, result);
    }
    return result;
  }

  public void fetchSubmodule(@NotNull Repository db,
                             @NotNull URIish fetchURI,
                             @NotNull Collection<RefSpec> refSpecs,
                             @NotNull AuthSettings auth) throws IOException, VcsException {
    if (alreadyFetched(fetchURI, refSpecs))
      return;
    try {
      myCommitLoader.fetch(db, fetchURI, refSpecs, new FetchSettings(auth));
    } finally {
      markAsFetched(fetchURI, refSpecs);
    }
  }

  private boolean alreadyFetched(@NotNull URIish uri, @NotNull Collection<RefSpec> refSpecs) {
    return myAlreadyFetched.contains(makeKey(uri, refSpecs));
  }

  private void markAsFetched(@NotNull URIish uri, @NotNull Collection<RefSpec> refSpecs) {
    myAlreadyFetched.add(makeKey(uri, refSpecs));
  }

  @NotNull
  private String makeKey(@NotNull URIish uri, @NotNull Collection<RefSpec> refSpecs) {
    StringBuilder key = new StringBuilder();
    key.append(uri.toASCIIString());
    for (RefSpec refSpec : refSpecs) {
      key.append(refSpec.toString());
    }
    return key.toString();
  }

  public VcsException wrapException(Exception ex) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("The error during GIT vcs operation " + myOperation, ex);
    }
    if (ex instanceof VcsException) {
      return (VcsException)ex;
    }

    String message;
    if (ex instanceof TransportException && ex.getCause() != null) {
      Throwable t = ex.getCause();
      if (t instanceof FileNotFoundException) {
        message = "File not found: " + t.getMessage();
      } else if (t instanceof UnknownHostException) {
        message = "Unknown host: " + t.getMessage();
      } else {
        message = t.toString();
      }
    } else if (ex instanceof NoRemoteRepositoryException) {
      message = "cannot locate repository at " + ex.getMessage();
    } else {
      message = ex.toString();
    }
    return new VcsException(StringUtil.capitalize(myOperation) + " failed: " + message, ex);
  }

  /**
   * Release all resources acquired during operation
   */
  public void close() {
    RuntimeException e = null;
    for (Repository r : myRepositories.values()) {
      try {
        myRepositoryManager.closeRepository(r);
      } catch (RuntimeException ex) {
        LOG.error("Exception during closing repository: " + r, ex);
        e = ex;
      }
    }
    if (e != null) {
      throw e;
    }
  }

  /**
   * Adds tree to tree walker.
   * If we should checkout submodules - adds submodule-aware tree iterator
   */
  public void addTree(@NotNull GitVcsRoot root,
                      @NotNull TreeWalk tw,
                      @NotNull Repository db,
                      @NotNull RevCommit commit,
                      boolean ignoreSubmodulesErrors) throws IOException, VcsException {
    addTree(root, tw, db, commit, ignoreSubmodulesErrors, true, null);
  }

  public void addTree(@NotNull GitVcsRoot root,
                      @NotNull TreeWalk tw,
                      @NotNull Repository db,
                      @NotNull RevCommit commit,
                      boolean ignoreSubmodulesErrors,
                      boolean logSubmoduleErrors,
                      @Nullable CheckoutRules rules) throws IOException, VcsException {
    if (root.isCheckoutSubmodules()) {
      SubmoduleResolverImpl submoduleResolver = new SubmoduleResolverImpl(this, myCommitLoader, db, commit, "");
      SubmodulesCheckoutPolicy checkoutPolicy = getPolicyWithErrorsIgnored(root.getSubmodulesCheckoutPolicy(), ignoreSubmodulesErrors);
      tw.addTree(create(db, commit, submoduleResolver, root.getRepositoryFetchURL().toString(), "", checkoutPolicy, logSubmoduleErrors, rules));
    } else {
      tw.addTree(commit.getTree().getId());
    }
  }

  @Nullable
  public RevCommit findCommit(@NotNull Repository r, @NotNull String sha) {
    return myCommitLoader.findCommit(r, sha);
  }

  @NotNull
  public GitVcsRoot makeRootWithTags() throws VcsException {
    GitVcsRoot gitRoot = getGitRoot(myRoot);
    gitRoot.setReportTags(true);
    return gitRoot;
  }

  @NotNull
  public GitProgress getProgress() {
    return myProgress;
  }

  @NotNull
  public RepositoryManager getRepositoryManager() {
    return myRepositoryManager;
  }

  @NotNull
  public ServerPluginConfig getPluginConfig() {
    return myPluginConfig;
  }
}
