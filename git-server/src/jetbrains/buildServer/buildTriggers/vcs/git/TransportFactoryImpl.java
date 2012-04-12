/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.Cipher;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jetbrains.buildServer.buildTriggers.vcs.git.ssh.PasswordSshSessionFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.ssh.PrivateKeyFileSshSessionFactory;
import jetbrains.buildServer.buildTriggers.vcs.git.ssh.RefreshableSshConfigSessionFactory;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static java.util.Collections.emptySet;

/**
* @author dmitry.neverov
*/
public class TransportFactoryImpl implements TransportFactory {

  private static Logger LOG = Logger.getInstance(TransportFactoryImpl.class.getName());

  private final ServerPluginConfig myConfig;
  /**
   * The default SSH session factory used for not explicitly configured host
   * It fails when user is prompted for some information.
   */
  private final RefreshableSshConfigSessionFactory mySshSessionFactory;
  /**
   * This factory is used when known host database is specified to be ignored
   */
  private final RefreshableSshConfigSessionFactory mySshSessionFactoryKnownHostsIgnored;

  private final SshSessionFactory myPasswordSshSessionFactory;
  private final Map<String,String> myJSchOptions;

  public TransportFactoryImpl(@NotNull ServerPluginConfig config) {
    this(config, null);
  }


  public TransportFactoryImpl(@NotNull ServerPluginConfig config, @Nullable final EventDispatcher<BuildServerListener> dispatcher) {
    myConfig = config;
    myJSchOptions = getJSchCipherOptions();
    final boolean monitorSshConfigs = dispatcher != null; //dispatcher is null in tests and when invoked from the Fetcher
    mySshSessionFactory = new RefreshableSshConfigSessionFactory(myConfig, monitorSshConfigs, myJSchOptions);
    mySshSessionFactoryKnownHostsIgnored = new RefreshableSshConfigSessionFactory(myConfig, monitorSshConfigs, myJSchOptions) {
      // note that different instance is used because JSch cannot be shared with strict host checking
      public Session getSession(String user, String pass, String host, int port, CredentialsProvider credentialsProvider, FS fs) throws JSchException {
        final Session session = super.getSession(user, pass, host, port, credentialsProvider, fs);
        session.setConfig("StrictHostKeyChecking", "no");
        return session;
      }
    };
    myPasswordSshSessionFactory = new PasswordSshSessionFactory(myConfig, myJSchOptions);
    if (monitorSshConfigs) {
      dispatcher.addListener(new BuildServerAdapter() {
        @Override
        public void serverShutdown() {
          mySshSessionFactory.stopMonitoringConfigs();
          mySshSessionFactoryKnownHostsIgnored.stopMonitoringConfigs();
        }
      });
    }
  }


  public Transport createTransport(Repository r, URIish url, Settings.AuthSettings authSettings) throws NotSupportedException, VcsException {
    final URIish authUrl = authSettings.createAuthURI(url);
    checkUrl(url);
    final Transport t = Transport.open(r, authUrl);
    t.setCredentialsProvider(authSettings.toCredentialsProvider());
    if (t instanceof SshTransport) {
      SshTransport ssh = (SshTransport)t;
      ssh.setSshSessionFactory(getSshSessionFactory(authSettings, url));
    }
    t.setTimeout(myConfig.getIdleTimeoutSeconds());
    return t;
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
    if (!url.isRemote()) {
      File localRepository = new File(url.getPath());
      if (!localRepository.exists()) {
        String error = "Cannot access repository " + url.toString();
        if (SystemInfo.isWindows) {
          error += ". If TeamCity is run as a Windows service, it cannot access network mapped drives. Make sure this is not your case.";
        }
        throw new VcsException(error);
      }
    }
  }


  /**
   * Get appropriate session factory object for specified settings and url
   *
   * @param authSettings a vcs root settings
   * @param url URL of interest
   * @return session factory object
   * @throws VcsException in case of problems with creating object
   */
  private SshSessionFactory getSshSessionFactory(Settings.AuthSettings authSettings, URIish url) throws VcsException {
    switch (authSettings.getAuthMethod()) {
      case PRIVATE_KEY_DEFAULT:
        return authSettings.isIgnoreKnownHosts() ? mySshSessionFactoryKnownHostsIgnored : mySshSessionFactory;
      case PRIVATE_KEY_FILE:
        try {
          return new PrivateKeyFileSshSessionFactory(myConfig, authSettings, myJSchOptions);
        } catch (VcsAuthenticationException e) {
          //add url to exception
          throw new VcsAuthenticationException(url.toString(), e.getMessage());
        }
      case PASSWORD:
        return myPasswordSshSessionFactory;
      default:
        throw new VcsAuthenticationException(url.toString(), "The authentication method " + authSettings.getAuthMethod() + " is not supported for SSH");
    }
  }

  /**
   * JSch checks available ciphers during session connect
   * (inside method send_kexinit), which is expensive (see
   * thread dumps attached to TW-18811). This method checks
   * available ciphers and if they are found turns off checks
   * inside JSch.
   * @return map of cipher options for JSch which either
   * specify found ciphers and turn off expensive cipher check,
   * or, when no ciphers found, do nothing, so we will get
   * exception from JSch with explanation of the problem
   */
  private Map<String, String> getJSchCipherOptions() {
    LOG.debug("Check available ciphers");
    try {
      JSch jsch = new JSch();
      Session session = jsch.getSession("", "");

      String cipherc2s = session.getConfig("cipher.c2s");
      String ciphers2c = session.getConfig("cipher.s2c");

      Set<String> notAvailable = checkCiphers(session);
      if (!notAvailable.isEmpty()) {
        cipherc2s = diffString(cipherc2s, notAvailable);
        ciphers2c = diffString(ciphers2c, notAvailable);
        if (isEmpty(cipherc2s) || isEmpty(ciphers2c)) {
          LOG.debug("No ciphers found, use default JSch options");
          return new HashMap<String, String>();
        }
      }

      LOG.debug("Turn off ciphers checks, use found ciphers cipher.c2s: " + cipherc2s + ", cipher.s2c: " + ciphers2c);
      Map<String, String> options = new HashMap<String, String>();
      options.put("cipher.c2s", cipherc2s);
      options.put("cipher.s2c", ciphers2c);
      options.put("CheckCiphers", "");//turn off ciphers check
      return options;
    } catch (JSchException e) {
      LOG.debug("Error while ciphers check, use default JSch options", e);
      return new HashMap<String, String>();
    }
  }

  private Set<String> checkCiphers(@NotNull Session session) {
    String ciphers = session.getConfig("CheckCiphers");
    if (isEmpty(ciphers))
      return emptySet();

    Set<String> result = new HashSet<String>();
    String[] _ciphers = ciphers.split(",");
    for (String cipher : _ciphers) {
      if (!checkCipher(session.getConfig(cipher)))
        result.add(cipher);
    }

    return result;
  }

  private boolean checkCipher(String cipherClassName){
    try {
      Class klass = Class.forName(cipherClassName);
      Cipher cipher = (Cipher)(klass.newInstance());
      cipher.init(Cipher.ENCRYPT_MODE,
                  new byte[cipher.getBlockSize()],
                  new byte[cipher.getIVSize()]);
      return true;
    } catch(Exception e){
      return false;
    }
  }

  private String diffString(@NotNull String str, @NotNull Set<String> notAvailable) {
    List<String> ciphers = new ArrayList<String>(Arrays.asList(str.split(",")));
    ciphers.removeAll(notAvailable);

    StringBuilder builder = new StringBuilder();
    Iterator<String> iter = ciphers.iterator();
    while (iter.hasNext()) {
      String cipher = iter.next();
      builder.append(cipher);
      if (iter.hasNext())
        builder.append(",");
    }
    return builder.toString();
  }

}
