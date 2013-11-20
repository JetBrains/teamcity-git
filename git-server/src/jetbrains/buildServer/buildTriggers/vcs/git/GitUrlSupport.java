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

  @Nullable
  public Map<String, String> convertToVcsRootProperties(@NotNull String url) throws VcsException {
    VcsUrl vcsUrl = parseUrl(url);
    if (vcsUrl == null) return null;

    final String providerSchema = vcsUrl.getProviderSchema();
    if(!PROVIDER_SCHEMA_LIST.contains(providerSchema))
      return null;

    URIish uri;
    try {
      uri = new URIish(vcsUrl.getProviderSpecificPart());
    } catch (URISyntaxException e) {
      throw new VcsException(e.getMessage(), e);
    }

    Map<String, String> result = new HashMap<String, String>();
    result.put(Constants.FETCH_URL, vcsUrl.getProviderSpecificPart());
    result.put(Constants.USERNAME, vcsUrl.getUsername());
    final boolean scpSyntax = isScpSyntax(uri);
    if (scpSyntax || "ssh".equals(uri.getScheme())) {
      if (scpSyntax && vcsUrl.getUsername() == null) {
        result.put(Constants.USERNAME, uri.getUser());
      }
      result.put(Constants.AUTH_METHOD, AuthenticationMethod.PRIVATE_KEY_DEFAULT.toString());
      result.put(Constants.IGNORE_KNOWN_HOSTS, "true");
    } else if (vcsUrl.getPassword() != null && !StringUtil.isEmptyOrSpaces(vcsUrl.getProviderSpecificPart())) {
      result.put(Constants.AUTH_METHOD, AuthenticationMethod.PASSWORD.toString());
      result.put(Constants.PASSWORD, vcsUrl.getPassword());
    }
    return result;
  }

  private VcsUrl parseUrl(@NotNull final String url) {
    try {
      return new VcsUrl(url);
    } catch (MalformedURLException e) {
      return null;
    }
  }

  private boolean isScpSyntax(URIish uriish) {
    return uriish.getScheme() == null;
  }
}
