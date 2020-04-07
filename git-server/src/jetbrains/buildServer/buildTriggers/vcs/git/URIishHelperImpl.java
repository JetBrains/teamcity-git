/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URISyntaxException;

/**
 * Implementation of {@link URIishHelper}.
 * The same as in the agent module.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public class URIishHelperImpl implements URIishHelper {

  @Override
  @Nullable
  public String getUserNameFromUrl(final String url) {
    try {
      URIish u = new URIish(url);
      return u.getUser();
    } catch (URISyntaxException e) {
      //ignore
    }
    return null;
  }

  @Override
  public CommonURIish createAuthURI(@NotNull final AuthSettings authSettings, @Nullable final String uri) throws VcsException {
    return createAuthURI(authSettings, uri, true);
  }

  @Override
  public CommonURIish createAuthURI(@NotNull final AuthSettings authSettings, @Nullable final String uri, final boolean fixErrors) throws VcsException {
    return createAuthURI(authSettings, createURI(uri), fixErrors);
  }

  @NotNull
  public CommonURIish createURI(@Nullable String uri) throws VcsException {
    try {
      return new CommonURIishImpl(new URIish(uri));
    } catch (Exception e) {
      if (uri != null && ReferencesResolverUtil.containsReference(uri))
        throw new VcsException("Unresolved parameter in url: " + uri, e);
      throw new VcsException("Invalid URI: " + uri, e);
    }
  }

  @Override
  public CommonURIish createAuthURI(@NotNull final AuthSettings authSettings, @NotNull final CommonURIish uri) {
    return createAuthURI(authSettings, uri, true);
  }

  @Override
  public CommonURIish createAuthURI(@NotNull final AuthSettings authSettings, @NotNull final CommonURIish uri, final boolean fixErrors) {
    URIish result = uri.get();
    if (requiresCredentials(result)) {
      if (!StringUtil.isEmptyOrSpaces(authSettings.getUserName())) {
        result = result.setUser(authSettings.getUserName());
      }
      if (!StringUtil.isEmpty(authSettings.getPassword())) {
        result = result.setPass(authSettings.getPassword());
      }
    }
    if (fixErrors && isAnonymousProtocol(result)) {
      result = result.setUser(null);
      result = result.setPass(null);
    }
    return new CommonURIishImpl(result);
  }

  private static boolean isAnonymousProtocol(@NotNull URIish uriish) {
    return "git".equals(uriish.getScheme());
  }

  public static boolean requiresCredentials(URIish result) {
    if (result.getHost() == null) return false;
    return !isAnonymousProtocol(result);
  }
}
