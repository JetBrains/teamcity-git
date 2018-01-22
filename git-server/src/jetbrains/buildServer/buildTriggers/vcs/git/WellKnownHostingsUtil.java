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
  public static VcsHostingRepo getGitlabRepo(@NotNull URIish uri) {
    if (!"gitlab.com".equals(uri.getHost()))
      return null;

    return ownerProjectStyleRepo("https://gitlab.com/", uri);
  }

  @Nullable
  public static VcsHostingRepo getBitbucketRepo(@NotNull URIish uri) {
    if (!"bitbucket.org".equals(uri.getHost()))
      return null;

    return ownerProjectStyleRepo("https://bitbucket.org/", uri);
  }

  @Nullable
  public static VcsHostingRepo getBitbucketServerRepo(@NotNull URIish uri) {
    String host = uri.getHost();
    if (host == null)
      return null;

    String path = uri.getPath();
    if (uri.getScheme() != null && uri.getScheme().startsWith("http") && path.endsWith(".git") && (path.startsWith("/scm/") || path.startsWith("/git/"))) {
      // probably Bitbucket server
      String ownerAndRepo = path.substring(5); // length of /scm/ or /git/
      int slashIdx = ownerAndRepo.indexOf('/');
      if (slashIdx == -1) return null;
      String owner = ownerAndRepo.substring(0, slashIdx);
      String repo = ownerAndRepo.substring(slashIdx+1, ownerAndRepo.length() - ".git".length());
      if (repo.contains("/")) return null;

      boolean personalRepo = '~' == owner.charAt(0);
      if (personalRepo) {
        owner = owner.substring(1);
      }

      String hostAndPort = host;
      if (uri.getPort() > 0 && uri.getPort() != 80 && uri.getPort() != 443) {
        hostAndPort += ":" + uri.getPort();
      }

      if (personalRepo) {
        return new VcsHostingRepo(uri.getScheme() + "://" + hostAndPort + "/users/" + owner + "/repos/" + repo, owner, repo);
      } else {
        return new VcsHostingRepo(uri.getScheme() + "://" + hostAndPort + "/projects/" + owner + "/repos/" + repo, owner, repo);
      }
    }

    return null;
  }

  @Nullable
  public static VcsHostingRepo getVSTSRepo(@NotNull URIish uri) {
    String host = uri.getHost();
    if (host == null)
      return null;

    final int idx = host.indexOf(".visualstudio.com");
    if (idx <= 0)
      return null;

    String owner = host.substring(0, idx);

    String path = uri.getPath();
    if (path == null)
      return null;

    int gitPrefixIdx = path.indexOf("_git/");
    if (gitPrefixIdx == -1) return null;

    String repoName = path.substring(gitPrefixIdx + "_git/".length());

    return new VcsHostingRepo("https://" + host + path, owner, repoName);
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
