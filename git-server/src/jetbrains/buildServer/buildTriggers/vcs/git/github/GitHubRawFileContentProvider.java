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

package jetbrains.buildServer.buildTriggers.vcs.git.github;

import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.util.Base64;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class GitHubRawFileContentProvider extends GitAbstractVcsFileContentProvider {

  private final GitAbstractVcsFileContentProvider myGenericProvider;
  private final String myOwner;
  private final String myRepository;

  public GitHubRawFileContentProvider(@NotNull GitVcsSupport vcs,
                                      @NotNull GitAbstractVcsFileContentProvider genericProvider,
                                      @NotNull String owner,
                                      @NotNull String repository) {
    super(vcs);
    myGenericProvider = genericProvider;
    myOwner = owner;
    myRepository = repository;
  }

  @NotNull
  public byte[] getContent(@NotNull String filePath,
                           @NotNull VcsRoot root,
                           @NotNull String version) throws VcsException {
    OperationContext ctx = myVcs.createContext(root, "retrieving content");
    InputStream is = null;
    try {
      URLConnection conn = getConnection(ctx.getGitRoot(), filePath, version);
      is = conn.getInputStream();
      return readStream(is);
    } catch (Exception e) {
      //LOG
      return myGenericProvider.getContent(filePath, root, version);
    } finally {
      ctx.close();
      if (is != null) {
        try {
          is.close();
        } catch (IOException e) {
        }
      }
    }
  }

  private URLConnection getConnection(@NotNull GitVcsRoot root, @NotNull String filePath, @NotNull String version) throws IOException {
    URL url = new URL("https://raw.github.com/" + myOwner + "/" + myRepository + "/" + version + "/" + filePath);
    URLConnection c = url.openConnection();
    AuthSettings auth = root.getAuthSettings();
    if (auth.getAuthMethod() == AuthenticationMethod.PASSWORD && auth.getUserName() != null && auth.getPassword() != null) {
      String credentials = auth.getUserName() + ":" + auth.getPassword();
      c.setRequestProperty("Authorization", "Basic " + Base64.encodeBytes(credentials.getBytes("UTF-8")));
    }
    return c;
  }

  private byte[] readStream(@NotNull InputStream is) throws IOException {
    byte[] buffer = new byte[8192];
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int read = 0;
    while ((read = is.read(buffer, 0, buffer.length)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }
}
