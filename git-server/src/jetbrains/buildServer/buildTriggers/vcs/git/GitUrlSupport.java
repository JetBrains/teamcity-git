/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author dmitry.neverov
 */
public class GitUrlSupport implements UrlSupport {

  private final GitVcsSupport myGitSupport;

  public GitUrlSupport(@NotNull GitVcsSupport gitSupport) {
    myGitSupport = gitSupport;
  }

  @Nullable
  public Map<String, String> convertToVcsRootProperties(@NotNull VcsUrl url) throws VcsException {
    String scmName = getMavenScmName(url);
    if (scmName != null && !"git".equals(scmName) && !"ssh".equals(scmName)) //some other scm provider
      return null;

    String fetchUrl = getFetchUrl(url);

    URIish uri;
    try {
      uri = new URIish(fetchUrl);
    } catch (URISyntaxException e) {
      throw new VcsException(e.getMessage(), e);
    }

    Map<String, String> props = new HashMap<String, String>(myGitSupport.getDefaultVcsProperties());
    props.put(Constants.FETCH_URL, fetchUrl);
    props.putAll(getAuthSettings(url, uri));

    if ("git".equals(scmName) || "git".equals(uri.getScheme()) || uri.getPath().endsWith(".git")) //git protocol, or git scm provider, or .git suffix
      return props;

    try {
      if (!fetchUrl.endsWith(".git")) {
        props.put(Constants.FETCH_URL, fetchUrl + ".git");
      }

      myGitSupport.testConnection(new VcsRootImpl(-1, Constants.VCS_NAME, props));
      return props;
    } catch (VcsException e) {
      return null; // probably not git
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
}
