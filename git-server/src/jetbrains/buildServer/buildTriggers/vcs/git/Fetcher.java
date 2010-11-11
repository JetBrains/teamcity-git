/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;

/**
 * Method main of this class is supposed to be run in separate process to avoid OutOfMemoryExceptions in server's process
 *
 * @author dmitry.neverov
 */
public class Fetcher {

  public static void main(String[] args) throws IOException, VcsException, URISyntaxException {
    Map<String, String> properties = VcsRootImpl.stringToProperties(readInput());
    String repositoryPath = properties.remove(Constants.REPOSITORY_DIR_PROPERTY_NAME);
    fetch(new File(repositoryPath), properties);
  }

  /**
   * Do fetch in directory <code>repositoryDir</code> with vcsRootProperties from <code>vcsRootProperties</code>
   *
   * @param repositoryDir     directory where run fetch
   * @param vcsRootProperties properties of vcsRoot
   * @throws IOException
   * @throws VcsException
   * @throws URISyntaxException
   */
  private static void fetch(final File repositoryDir, Map<String, String> vcsRootProperties) throws IOException, VcsException, URISyntaxException {
    final String fetchUrl = vcsRootProperties.get(Constants.FETCH_URL);
    final String refspec = vcsRootProperties.get(Constants.REFSPEC);
    Settings.AuthSettings auth = new Settings.AuthSettings(vcsRootProperties);
    //this method should be called with repository lock, but Fetcher is ran in separate process, so
    //locks will not help; Fetcher is ran after we have ensured that repository exists, so we can call it without lock
    Repository repository = GitServerUtil.getRepository(repositoryDir, new URIish(fetchUrl));

    GitVcsSupport gitSupport = new GitVcsSupport(new ServerPaths(), null);
    final Transport tn = gitSupport.openTransport(auth, repository, new URIish(fetchUrl));
    try {
      RefSpec spec = new RefSpec(refspec).setForceUpdate(true);
      tn.fetch(NullProgressMonitor.INSTANCE, Collections.singletonList(spec));
    } finally {
      tn.close();
    }
  }

  /**
   * Read input from System.in until it closed
   *
   * @return input as string
   * @throws IOException
   */
  private static String readInput() throws IOException {
    char[] chars = new char[512];
    StringBuilder sb = new StringBuilder();
    Reader processInput = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
    int count = 0;
    while ((count = processInput.read(chars)) != -1) {
      final String str = new String(chars, 0, count);
      sb.append(str);
    }
    return sb.toString();
  }

}
