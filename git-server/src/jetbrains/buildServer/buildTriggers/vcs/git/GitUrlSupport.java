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

import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author dmitry.neverov
 */
public class GitUrlSupport implements UrlSupport {

  private static final String PROVIDER_SCHEMA = "git:ssh";
  private static final Collection<String> PROVIDER_SCHEMA_LIST = StringUtil.split(PROVIDER_SCHEMA, ":");
  private final GitVcsSupport myGitSupport;

  public GitUrlSupport(@NotNull GitVcsSupport gitSupport) {
    myGitSupport = gitSupport;
  }

  @Nullable
  public Map<String, String> convertToVcsRootProperties(@NotNull VcsUrl url) throws VcsException {
    String fetchUrl = url.getUrl();
    boolean testRequired = !fetchUrl.contains("git");
    MavenVcsUrl vcsUrl = url.asMavenVcsUrl();
    if (vcsUrl != null) {
      final String providerSchema = vcsUrl.getProviderSchema();
      if(!PROVIDER_SCHEMA_LIST.contains(providerSchema))
        return null;

      fetchUrl = vcsUrl.getProviderSpecificPart();
      testRequired = false;
    }

    URIish uri;
    try {
      uri = new URIish(fetchUrl);
    } catch (URISyntaxException e) {
      throw new VcsException(e.getMessage(), e);
    }

    Credentials credentials = url.getCredentials();
    Map<String, String> props = new HashMap<String, String>(myGitSupport.getDefaultVcsProperties());
    props.put(Constants.FETCH_URL, fetchUrl);
    props.put(Constants.AUTH_METHOD, AuthenticationMethod.ANONYMOUS.toString());

    if (credentials != null) {
      props.put(Constants.AUTH_METHOD, AuthenticationMethod.PASSWORD.toString());
      props.put(Constants.USERNAME, credentials.getUsername());
      props.put(Constants.PASSWORD, credentials.getPassword());
    }

    final boolean scpSyntax = isScpSyntax(uri);
    if (scpSyntax || "ssh".equals(uri.getScheme())) {
      if (scpSyntax && credentials == null) {
        props.put(Constants.USERNAME, uri.getUser());
      }
      props.put(Constants.AUTH_METHOD, AuthenticationMethod.PRIVATE_KEY_DEFAULT.toString());
    }

    if (testRequired) {
      try {
        myGitSupport.getCurrentVersion(new VcsRootImpl(-1, Constants.VCS_NAME, props));
      } catch (VcsException e) {
        if (e.getCause() instanceof NoRemoteRepositoryException) {
          return null; // definitely not git
        }
      }
    }

    return props;
  }

  private boolean isScpSyntax(URIish uriish) {
    return uriish.getScheme() == null;
  }
}
