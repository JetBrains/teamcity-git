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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.jcraft.jsch.Cipher;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;

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
  private final Map<String,String> myJSchOptions;

  public TransportFactoryImpl(@NotNull ServerPluginConfig config) {
    myConfig = config;
    myJSchOptions = getJSchCipherOptions();
  }


  public Transport createTransport(@NotNull Repository r, @NotNull URIish url, @NotNull Settings.AuthSettings authSettings) throws NotSupportedException, VcsException {
    try {
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
    } catch (TransportException e) {
      throw new VcsException("Cannot create transport", e);
    }
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
        return new DefaultJschConfigSessionFactory(myConfig, authSettings, myJSchOptions);
      case PRIVATE_KEY_FILE:
        return new CustomPrivateKeySessionFactory(myConfig, authSettings, myJSchOptions);
      case PASSWORD:
        return new PasswordJschConfigSessionFactory(myConfig, authSettings, myJSchOptions);
      default:
        throw new VcsAuthenticationException(url.toString(), "The authentication method " + authSettings.getAuthMethod() + " is not supported for SSH");
    }
  }


  private static class DefaultJschConfigSessionFactory extends JschConfigSessionFactory {
    protected final ServerPluginConfig myConfig;
    protected final Settings.AuthSettings myAuthSettings;
    private final Map<String,String> myJschOptions;

    private DefaultJschConfigSessionFactory(@NotNull ServerPluginConfig config,
                                            @NotNull Settings.AuthSettings authSettings,
                                            @NotNull Map<String,String> jschOptions) {
      myConfig = config;
      myAuthSettings = authSettings;
      myJschOptions = jschOptions;
    }

    @Override
    protected void configure(OpenSshConfig.Host hc, Session session) {
      session.setProxy(myConfig.getJschProxy());//null proxy is allowed
      if (myAuthSettings.isIgnoreKnownHosts())
        session.setConfig("StrictHostKeyChecking", "no");
      if (!myConfig.alwaysCheckCiphers()) {
        for (Map.Entry<String, String> entry : myJschOptions.entrySet())
          session.setConfig(entry.getKey(), entry.getValue());
      }
    }
  }

  private static class PasswordJschConfigSessionFactory extends DefaultJschConfigSessionFactory {

    private PasswordJschConfigSessionFactory(@NotNull ServerPluginConfig config,
                                             @NotNull Settings.AuthSettings authSettings,
                                             @NotNull Map<String,String> jschOptions) {
      super(config, authSettings, jschOptions);
    }

    @Override
    protected void configure(OpenSshConfig.Host hc, Session session) {
      super.configure(hc, session);
      session.setPassword(myAuthSettings.getPassword());
    }
  }


  private static class CustomPrivateKeySessionFactory extends DefaultJschConfigSessionFactory {

    private CustomPrivateKeySessionFactory(@NotNull ServerPluginConfig config,
                                           @NotNull Settings.AuthSettings authSettings,
                                           @NotNull Map<String,String> jschOptions) {
      super(config, authSettings, jschOptions);
    }

    @Override
    protected JSch getJSch(OpenSshConfig.Host hc, FS fs) throws JSchException {
      return createDefaultJSch(fs);
    }

    @Override
    protected JSch createDefaultJSch(FS fs) throws JSchException {
      final JSch jsch = new JSch();
      jsch.addIdentity(myAuthSettings.getPrivateKeyFilePath(), myAuthSettings.getPassphrase());
      return jsch;
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
