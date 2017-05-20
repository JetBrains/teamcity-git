/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WellKnownHostingsUtil {
  private WellKnownHostingsUtil() {}

  @Nullable
  public static VcsHostingRepo getGitHubRepo(@NotNull URIish uri) {
    if (!"github.com".equals(uri.getHost()))
      return null;

    return ownerProjectStyleRepo("https://github.com/", uri);
  }

  @Nullable
  public static VcsHostingRepo getBitbucketRepo(@NotNull URIish uri) {
    if (!"bitbucket.org".equals(uri.getHost()))
      return null;

    return ownerProjectStyleRepo("https://bitbucket.org/", uri);
  }

  @Nullable
  public static VcsHostingRepo getVSTSRepo(@NotNull URIish uri) {
    if (!uri.getHost().contains("visualstudio.com"))
      return null;

    return ownerProjectStyleRepo("https://" + uri.getHost() + "/", uri);
  }

  private static VcsHostingRepo ownerProjectStyleRepo(@NotNull String hostingUrl, @NotNull URIish uri) {
    String path = uri.getPath();
    if (path == null)
      return null;
    if (path.startsWith("/"))
      path = path.substring(1);
    int idx = path.indexOf("/");
    if (idx <= 0)
      return null;
    String owner = path.substring(0, idx);
    String repo = path.substring(idx + 1, path.length());
    if (repo.endsWith(".git"))
      repo = repo.substring(0, repo.length() - 4);
    return new VcsHostingRepo(hostingUrl + owner + "/" + repo, owner, repo);
  }

}
