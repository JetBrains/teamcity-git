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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.oauth.TokenRefresher;
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
import org.eclipse.egit.github.core.client.RequestException;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dmitry.neverov
 */
public class GitUrlSupport implements ContextAwareUrlSupport, PositionAware, GitServerExtension {

  private static final String REMOTE_HEAD_NOT_FOUND = "Cannot determine remote HEAD";
  private static final String MULTIPLE_REMOTE_HEAD = "Multiple remote HEAD branches";

  private static final String[] POSSIBLE_DEFAULT_BRANCHES = {"main", "default", "development", "develop", "primary", "trunk"};

  private final GitVcsSupport myGitSupport;
  private final TokenRefresher myTokenRefresher;
  private volatile ExtensionsProvider myExtensionsProvider;
  private volatile ProjectManager myProjectManager;

  public GitUrlSupport(@NotNull GitVcsSupport gitSupport, @NotNull TokenRefresher tokenRefresher) {
    myGitSupport = gitSupport;
    myTokenRefresher = tokenRefresher;
    gitSupport.addExtension(this);
  }

  // project manager will be set by {@link GitUrlSupportInitializer}
  // have to do it this way because Upsource loads Git plugin and it does not have project manager bean
  public void setProjectManager(@NotNull final ProjectManager projectManager) {
    myProjectManager = projectManager;
  }

  // ExtensionsProvider will be set by {@link GitUrlSupportInitializer}
  // have to do it this way because Upsource loads Git plugin and it does not have ExtensionsProvider bean
  public void setExtensionsProvider(@NotNull final ExtensionsProvider extensionsProvider) {
    myExtensionsProvider = extensionsProvider;
  }

  private static boolean isBranchRelatedError(@NotNull VcsException e) {
    if (isDefaultBranchNotFound(e)) return true;
    final String message = e.getMessage();
    return StringUtil.isNotEmpty(message) &&
           (message.equals(REMOTE_HEAD_NOT_FOUND) || message.equals(GitVcsSupport.GIT_REPOSITORY_HAS_NO_BRANCHES) || message.equals(MULTIPLE_REMOTE_HEAD));
  }

  private static boolean isDefaultBranchNotFound(@NotNull VcsException e) {
    return e.getMessage().contains(GitVcsSupport.DEFAULT_BRANCH_REVISION_NOT_FOUND);
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

    final SProject curProject = myProjectManager == null ? null : myProjectManager.findProjectById(operationContext.getCurrentProjectId());

    Map<String, String> props = new HashMap<>(myGitSupport.getDefaultVcsProperties());
    props.put(Constants.FETCH_URL, fetchUrl);
    props.putAll(getAuthSettings(url, uri));

    VcsHostingRepo ghRepo = WellKnownHostingsUtil.getGitHubRepo(uri);
    if (ghRepo != null)
      refineGithubSettings(ghRepo, props, curProject);

    int numSshKeysTried = 0;

    if (AuthenticationMethod.PRIVATE_KEY_DEFAULT.toString().equals(props.get(Constants.AUTH_METHOD)) && fetchUrl.endsWith(".git") && curProject != null) {
      // SSH access, before using the default private key which may not be accessible on the agent,
      // let's iterate over all SSH keys of the current project, maybe we'll find a working one
      ServerSshKeyManager serverSshKeyManager = getSshKeyManager();
      if (serverSshKeyManager != null) {
        for (TeamCitySshKey key: serverSshKeyManager.getKeys(curProject)) {
          if (key.isEncrypted()) continue; // don't know password, so can't use it

          Map<String, String> propsCopy = new HashMap<>(props);
          propsCopy.put(Constants.AUTH_METHOD, AuthenticationMethod.TEAMCITY_SSH_KEY.toString());
          propsCopy.put(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME, key.getName());

          try {
            numSshKeysTried++;
            return testConnection(propsCopy, curProject);
          } catch (VcsException e) {
            if (isBranchRelatedError(e)) throw e;
          }
        }
      }

      // could not find any valid keys, proceed with default SSH key
      try {
        return testConnection(props, curProject);
      } catch (VcsException e) {
        if (isBranchRelatedError(e)) throw e;

        String message = "Could not connect to the Git repository by SSH protocol.";
        if (numSshKeysTried > 0) {
          message += " Tried " + numSshKeysTried + " SSH " + StringUtil.pluralize("key", numSshKeysTried) +
                     " accessible from the current project.";
        } else {
          message += " Could not find an SSH key in the current project which would work with this Git repository.";
        }

        throw new VcsException(message + " Error message: " + e.getMessage(), e);
      }
    }

    final boolean defaultBranchKnown = props.get(Constants.BRANCH_NAME) != null;
    if (defaultBranchKnown) {
      //git protocol, or git scm provider
      if ("git".equals(scmName) || "git".equals(uri.getScheme()) || fetchUrl.endsWith(".git")) return props;
    }

    // need to guess default branch or
    // not SSH or URL does not end with .git, still try to connect just for the case
    try {
      return testConnection(props, curProject);
    } catch (VcsException e) {
      if (isBranchRelatedError(e) || GitServerUtil.isAuthError(e) || fetchUrl.toLowerCase().contains("git")) throw e;

      // probably not git
      Loggers.VCS.infoAndDebugDetails("Failed to recognize " + url.getUrl() + " as a git repository", e);
      return null;
    }
  }

  @NotNull
  private Map<String, String> testConnection(@NotNull Map<String, String> props, @Nullable SProject curProject) throws VcsException {
    final TestConnectionSupport testConnectionSupport = myGitSupport.getTestConnectionSupport();
    assert testConnectionSupport != null;

    final VcsRoot vcsRoot = createDummyRoot(props, curProject);
    try {
      testConnectionSupport.testConnection(vcsRoot);
      props.putIfAbsent(Constants.BRANCH_NAME, "refs/heads/master");
      return props;
    } catch (VcsException e) {
      // in case default branch is unknown and "master" branch not advertised by the remote - try to guess default branch
      if (props.get(Constants.BRANCH_NAME) == null && isDefaultBranchNotFound(e)) {
        props.put(Constants.BRANCH_NAME, guessDefaultBranch(vcsRoot));
        return props;
      }
      throw e;
    }
  }

  // protected for tests
  @NotNull
  protected VcsRoot createDummyRoot(@NotNull Map<String, String> props, @Nullable SProject curProject) {
    return curProject == null ? new VcsRootImpl(-1, Constants.VCS_NAME, props) : curProject.createDummyVcsRoot(Constants.VCS_NAME, props);
  }

  // see git remote.c guess_remote_head method for details
  @NotNull
  private String guessDefaultBranch(@NotNull VcsRoot vcsRoot) throws VcsException{
    final Collection<Ref> remoteRefs = getRemoteRefs(vcsRoot);

    final Ref head = remoteRefs.stream().filter(r -> "HEAD".equals(r.getName())).findFirst().orElse(null);

    if (head == null) throw new VcsException(REMOTE_HEAD_NOT_FOUND);
    if (head.isSymbolic()) return head.getTarget().getName();

    final ObjectId headObjectId = head.getObjectId();
    if (headObjectId == null) throw new VcsException(REMOTE_HEAD_NOT_FOUND);

    final Set<String> candidates = remoteRefs.stream()
                                             .filter(r -> r.getName().startsWith("refs/heads/") && headObjectId.equals(r.getObjectId()))
                                             .map(Ref::getName)
                                             .collect(Collectors.toSet());
    if (candidates.isEmpty()) throw new VcsException(REMOTE_HEAD_NOT_FOUND);
    if (candidates.size() == 1) return candidates.iterator().next();

    for (String b : POSSIBLE_DEFAULT_BRANCHES) {
      if (candidates.contains(b)) return b;
    }

    throw new VcsException(MULTIPLE_REMOTE_HEAD);
  }

  private Collection<Ref> getRemoteRefs(@NotNull VcsRoot vcsRoot) throws VcsException {
    final OperationContext context = myGitSupport.createContext(vcsRoot, "get remote refs to detect default branch");
    try {
      return myGitSupport.getRepositoryManager().runWithDisabledRemove(context.getGitRoot().getRepositoryDir(), () -> myGitSupport.getRemoteRefs(context.getRoot()).values());
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
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

  private void refineGithubSettings(@NotNull VcsHostingRepo ghRepo, @NotNull Map<String, String> props, SProject curProject) throws VcsException {
    SVcsRoot vcsRoot = curProject.createDummyVcsRoot(Constants.VCS_NAME, props);
    AuthSettings auth = new AuthSettingsImpl(props, vcsRoot, new URIishHelperImpl(), tokenId -> myTokenRefresher.getRefreshableToken(curProject, tokenId, false));

    final String password = auth.getPassword();
    AuthenticationMethod authMethod = auth.getAuthMethod();
    GitHubClient client = new GitHubClient();
    if (authMethod.isPasswordBased() && StringUtil.isNotEmpty(password)) {
      if (ReferencesResolverUtil.containsReference(password) || password.length() != 40) return; // we can only proceed with PAT, or GitHub Apps connections

      if (auth.getUserName() != null) {
        client.setCredentials(auth.getUserName(), password);
      }
    }
    try {
      Repository r = IOGuard.allowNetworkCall(() -> new RepositoryService(client).getRepository(ghRepo.owner(), ghRepo.repoName()));
      props.put(Constants.BRANCH_NAME, GitUtils.expandRef(r.getMasterBranch()));
    } catch (RequestException r) {
      Loggers.VCS.warnAndDebugDetails("Failed to request details for the GitHub repository: " + ghRepo.repositoryUrl(), r);
      if (auth.getAuthMethod().isPasswordBased()) {
        if (r.getStatus() == 401) {
          throw new VcsAuthenticationException("Incorrect username or password/token"); // seems credentials are incorrect
        }

        throw new VcsAuthenticationException(r.getMessage()); // some limits exceeded?
      }
    } catch (IOException e) {
      Loggers.VCS.warnAndDebugDetails("Failed to request details for the GitHub repository: " + ghRepo.repositoryUrl(), e);
    }
  }

  @NotNull
  private Map<String, String> getAuthSettings(@NotNull VcsUrl url, @NotNull URIish uri) {
    Map<String, String> authSettings = new HashMap<String, String>();
    authSettings.put(Constants.AUTH_METHOD, getAuthMethod(url, uri).toString());
    Credentials credentials = url.getCredentials();
    if (credentials != null) {
      credentials.putToPropertyMap(authSettings);
    } else {
      authSettings.put(Constants.USERNAME, uri.getUser());
    }
    return authSettings;
  }

  private AuthenticationMethod getAuthMethod(@NotNull VcsUrl url, @NotNull URIish uri) {
    if (isScpSyntax(uri) || "ssh".equals(uri.getScheme()))
      return AuthenticationMethod.PRIVATE_KEY_DEFAULT;
    Credentials credentials = url.getCredentials();
    if (credentials != null)
      return credentials instanceof RefreshableTokenCredentials ? AuthenticationMethod.ACCESS_TOKEN : AuthenticationMethod.PASSWORD;
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
    if (myExtensionsProvider == null) return null;
    Collection<ServerSshKeyManager> managers = myExtensionsProvider.getExtensions(ServerSshKeyManager.class);
    if (managers.isEmpty())
      return null;
    return managers.iterator().next();
  }
}
