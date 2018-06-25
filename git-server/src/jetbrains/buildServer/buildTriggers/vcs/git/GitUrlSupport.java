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

import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.positioning.PositionAware;
import jetbrains.buildServer.util.positioning.PositionConstraint;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author dmitry.neverov
 */
public class GitUrlSupport implements ContextAwareUrlSupport, PositionAware, GitServerExtension {

  private final GitVcsSupport myGitSupport;
  private final ProjectManager myProjectManager;
  private final ExtensionsProvider myExtensionsProvider;

  public GitUrlSupport(@NotNull GitVcsSupport gitSupport,
                       @NotNull final ProjectManager projectManager,
                       @NotNull final ExtensionsProvider extensionsProvider) {
    myGitSupport = gitSupport;
    myProjectManager = projectManager;
    myExtensionsProvider = extensionsProvider;
    gitSupport.addExtension(this);
  }

  @Nullable
  public Map<String, String> convertToVcsRootProperties(@NotNull VcsUrl url, @NotNull VcsOperationContext operationContext) throws VcsException {
    String scmName = getMavenScmName(url);
    if (scmName != null && !"git".equals(scmName) && !"ssh".equals(scmName)) //some other scm provider
      return null;

    String fetchUrl = getFetchUrl(url);

    URIish uri = parseURIish(fetchUrl);

    if (fetchUrl.startsWith("https://") && !fetchUrl.endsWith(".git")) {
      VcsHostingRepo gitlabRepo = WellKnownHostingsUtil.getGitlabRepo(uri);
      if (gitlabRepo != null) {
        // for GitLab we need to add .git suffix to the fetch URL, otherwise, for some reason JGit can't work with this repository (although regular git command works)
        fetchUrl = fetchUrl + ".git";
        uri = parseURIish(fetchUrl);
      }
    }

    Map<String, String> props = new HashMap<>(myGitSupport.getDefaultVcsProperties());
    props.put(Constants.FETCH_URL, fetchUrl);
    props.putAll(getAuthSettings(url, uri));

    VcsHostingRepo ghRepo = WellKnownHostingsUtil.getGitHubRepo(uri);
    if (ghRepo != null)
      refineGithubSettings(ghRepo, props);

    int numSshKeysTried = 0;

    final TestConnectionSupport testConnectionSupport = myGitSupport.getTestConnectionSupport();
    assert testConnectionSupport != null;

    if (AuthenticationMethod.PRIVATE_KEY_DEFAULT.toString().equals(props.get(Constants.AUTH_METHOD)) && fetchUrl.endsWith(".git")) {
      // SSH access, before using the default private key which may not be accessible on the agent,
      // let's iterate over all SSH keys of the current project, maybe we'll find a working one
      SProject curProject = myProjectManager.findProjectById(operationContext.getCurrentProjectId());
      ServerSshKeyManager serverSshKeyManager = getSshKeyManager();
      if (curProject != null && serverSshKeyManager != null) {
        for (TeamCitySshKey key: serverSshKeyManager.getKeys(curProject)) {
          if (key.isEncrypted()) continue; // don't know password, so can't use it
          String keyName = key.getName();

          Map<String, String> propsCopy = new HashMap<>(props);
          propsCopy.put(Constants.AUTH_METHOD, AuthenticationMethod.TEAMCITY_SSH_KEY.toString());
          propsCopy.put(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME, keyName);

          SVcsRoot dummyVcsRoot = curProject.createDummyVcsRoot(Constants.VCS_NAME, propsCopy);

          try {
            numSshKeysTried++;
            testConnectionSupport.testConnection(dummyVcsRoot);
            return propsCopy;
          } catch (VcsException e) {
            if (GitVcsSupport.GIT_REPOSITORY_HAS_NO_BRANCHES.equals(e.getMessage()))
              throw e;
          }
        }
      }

      // could not find any valid keys, proceed with default SSH key
      try {
        testConnectionSupport.testConnection(new VcsRootImpl(-1, Constants.VCS_NAME, props));
        return props;
      } catch (VcsException e) {
        if (GitVcsSupport.GIT_REPOSITORY_HAS_NO_BRANCHES.equals(e.getMessage()))
          throw e;

        String message = "Could not connect to the Git repository by SSH protocol.";
        if (numSshKeysTried > 0) {
          message += " Tried " + numSshKeysTried + " SSH " + StringUtil.pluralize("key", numSshKeysTried) +
                     " accessible from the current project.";
        } else {
          message += " Could not find an SSH key in the current project which would work with this Git repository.";
        }

        throw new VcsException(message + " Error message: " + e.getMessage());
      }
    }

    if ("git".equals(scmName) || "git".equals(uri.getScheme()) || fetchUrl.endsWith(".git")) //git protocol, or git scm provider
      return props;

    // not SSH or URL does not end with .git, still try to connect just for the case
    try {
      testConnectionSupport.testConnection(new VcsRootImpl(-1, Constants.VCS_NAME, props));
      return props;
    } catch (VcsException e) {
      if (GitVcsSupport.GIT_REPOSITORY_HAS_NO_BRANCHES.equals(e.getMessage()))
        throw e;

      if (GitServerUtil.isAuthError(e)) {
        throw e;
      }

      return null; // probably not git
    }
  }

  @NotNull
  private URIish parseURIish(@NotNull String fetchUrl) throws VcsException {
    URIish uri;
    try {
      uri = new URIish(fetchUrl);
    } catch (URISyntaxException e) {
      throw new VcsException(e.getMessage(), e);
    }
    return uri;
  }

  private void refineGithubSettings(@NotNull VcsHostingRepo ghRepo, @NotNull Map<String, String> props) {
    GitHubClient client = new GitHubClient();
    AuthSettings auth = new AuthSettings(props);
    if (auth.getAuthMethod() == AuthenticationMethod.PASSWORD && auth.getUserName() != null && auth.getPassword() != null) {
      client.setCredentials(auth.getUserName(), auth.getPassword());
    }
    try {
      Repository r = new RepositoryService(client).getRepository(ghRepo.owner(), ghRepo.repoName());
      props.put(Constants.BRANCH_NAME, GitUtils.expandRef(r.getMasterBranch()));
    } catch (IOException e) {
      //ignore, cannot refine settings
    }
  }

  @NotNull
  private Map<String, String> getAuthSettings(@NotNull VcsUrl url, @NotNull URIish uri) {
    Map<String, String> authSettings = new HashMap<String, String>();
    authSettings.put(Constants.AUTH_METHOD, getAuthMethod(url, uri).toString());
    Credentials credentials = url.getCredentials();
    if (credentials != null) {
      authSettings.put(Constants.USERNAME, credentials.getUsername());
      authSettings.put(Constants.PASSWORD, credentials.getPassword());
    } else {
      authSettings.put(Constants.USERNAME, uri.getUser());
    }
    return authSettings;
  }

  private AuthenticationMethod getAuthMethod(@NotNull VcsUrl url, @NotNull URIish uri) {
    if (isScpSyntax(uri) || "ssh".equals(uri.getScheme()))
      return AuthenticationMethod.PRIVATE_KEY_DEFAULT;
    if (url.getCredentials() != null)
      return AuthenticationMethod.PASSWORD;
    return AuthenticationMethod.ANONYMOUS;
  }

  @Nullable
  private String getMavenScmName(@NotNull VcsUrl url) {
    MavenVcsUrl mavenUrl = url.asMavenVcsUrl();
    if (mavenUrl == null)
      return null;
    return mavenUrl.getProviderSchema();
  }

  @NotNull
  private String getFetchUrl(@NotNull VcsUrl url) {
    MavenVcsUrl mavenUrl = url.asMavenVcsUrl();
    if (mavenUrl != null)
      return mavenUrl.getProviderSpecificPart();
    return url.getUrl();
  }

  private boolean isScpSyntax(URIish uriish) {
    return uriish.getScheme() == null && uriish.isRemote();
  }

  @NotNull
  public String getOrderId() {
    return myGitSupport.getName();
  }

  @NotNull
  public PositionConstraint getConstraint() {
    return PositionConstraint.first(); // placed first to avoid problems with GitHub and SVN
  }

  @Nullable
  private synchronized ServerSshKeyManager getSshKeyManager() {
    Collection<ServerSshKeyManager> managers = myExtensionsProvider.getExtensions(ServerSshKeyManager.class);
    if (managers.isEmpty())
      return null;
    return managers.iterator().next();
  }
}
