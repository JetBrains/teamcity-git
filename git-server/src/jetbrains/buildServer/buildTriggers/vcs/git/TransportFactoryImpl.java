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

import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.jetbrains.annotations.NotNull;

/**
* @author dmitry.neverov
*/
public class TransportFactoryImpl implements TransportFactory {

  private final ServerPluginConfig myConfig;
  private final GitTrustStoreProvider myGitTrustStoreProvider;
  private final SshSessionMetaFactory mySshSessionMetaFactory;

  public TransportFactoryImpl(@NotNull ServerPluginConfig config,
                              @NotNull VcsRootSshKeyManager sshKeyManager) {
    this(config, new GitTrustStoreProviderStatic(null), new SshSessionMetaFactoryImpl(config, sshKeyManager));
  }

  public TransportFactoryImpl(@NotNull ServerPluginConfig config,
                              @NotNull GitTrustStoreProvider gitTrustStoreProvider,
                              @NotNull SshSessionMetaFactoryImpl sshSessionMetaFactory) {
    myConfig = config;
    myGitTrustStoreProvider = gitTrustStoreProvider;
    mySshSessionMetaFactory = sshSessionMetaFactory;
    String factoryName = myConfig.getHttpConnectionFactory();
    HttpConnectionFactory f;
    if ("httpClient".equals(factoryName)) {
      f = new SNIHttpClientConnectionFactory(() -> myGitTrustStoreProvider.getTrustStore());
    } else if ("httpClientNoSNI".equals(factoryName)) {
      f = new SSLHttpClientConnectionFactory(() -> myGitTrustStoreProvider.getTrustStore());
    } else {
      f = new TeamCityJDKHttpConnectionFactory(myConfig, () -> myGitTrustStoreProvider.getTrustStore());
    }
    HttpTransport.setConnectionFactory(f);
    JSchLoggers.initJSchLogger();
  }

  public Transport createTransport(@NotNull Repository r, @NotNull URIish url, @NotNull AuthSettings authSettings) throws NotSupportedException, VcsException {
    return createTransport(r, url, authSettings, myConfig.getIdleTimeoutSeconds());
  }


  public Transport createTransport(@NotNull final Repository r,
                                   @NotNull final URIish url,
                                   @NotNull final AuthSettings authSettings,
                                   final int timeoutSeconds) throws NotSupportedException, VcsException {
    try {
      checkUrl(url);
      URIish preparedURI = prepareURI(url);
      final Transport t = Transport.open(r, preparedURI);
      t.setCredentialsProvider(new AuthCredentialsProvider(authSettings));
      if (t instanceof SshTransport) {
        SshTransport ssh = (SshTransport)t;
        ssh.setSshSessionFactory(mySshSessionMetaFactory.getSshSessionFactory(url, authSettings));
      }
      t.setTimeout(timeoutSeconds);
      return t;
    } catch (TransportException e) {
      throw new VcsException("Cannot create transport", e);
    }
  }

  @NotNull
  private URIish prepareURI(@NotNull URIish uri) {
    final String scheme = uri.getScheme();
    //Remove a username from the http URI. A Username can contain forbidden
    //characters, e.g. backslash (TW-21747). A username and a password will
    //be supplied by CredentialProvider
    if ("http".equals(scheme) || "https".equals(scheme))
      return uri.setUser(null);
    return uri;
  }


  /**
   * This is a work-around for an issue http://youtrack.jetbrains.net/issue/TW-9933.
   * Due to bug in jgit (https://bugs.eclipse.org/bugs/show_bug.cgi?id=315564),
   * in the case of not-existing local repository we get an obscure exception:
   * 'org.eclipse.jgit.errors.NotSupportedException: URI not supported: x:/git/myrepo.git',
   * while URI is correct.
   *
   * It often happens when people try to access a repository located on a mapped network
   * drive from the TeamCity started as Windows service.
   *
   * If repository is local and is not exists this method throws a friendly exception.
   *
   * @param url URL to check
   * @throws VcsException if url points to not-existing local repository
   */
  private void checkUrl(final URIish url) throws VcsException {
    String scheme = url.getScheme();
    if (!url.isRemote() && !"http".equals(scheme) && !"https".equals(scheme)) {
      File localRepository = new File(url.getPath());
      if (!localRepository.exists()) {
        String error = "Cannot access the '" + url.toString() + "' repository";
        if (SystemInfo.isWindows) {
          error += ". If TeamCity is run as a Windows service, it cannot access network mapped drives. Make sure this is not your case.";
        }
        throw new VcsException(error);
      }
    }
  }
}
