package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import com.jcraft.jsch.Cipher;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.version.ServerVersionHolder;
import jetbrains.buildServer.version.ServerVersionInfo;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static java.util.Collections.emptySet;

public class SshSessionMetaFactoryImpl implements SshSessionMetaFactory {

  private static final Logger LOG = Logger.getInstance(SshSessionMetaFactoryImpl.class.getName());

  // the key in this map is url, because it's not expected, that related code will run concurrently for the same url
  private final ConcurrentHashMap<URIish, SshSessionFactory> myFactories = new ConcurrentHashMap<>();

  private final ServerPluginConfig myConfig;
  private final Map<String,String> myJSchOptions;
  private final VcsRootSshKeyManager mySshKeyManager;

  public SshSessionMetaFactoryImpl(@NotNull ServerPluginConfig config,
                                   @NotNull VcsRootSshKeyManager sshKeyManager) {
    myConfig = config;
    mySshKeyManager= sshKeyManager;
    myJSchOptions = getJSchCipherOptions();
    SshSessionFactory.setInstance(new SshSessionFactory() {
      @Override
      public RemoteSession getSession(URIish uri, CredentialsProvider credentialsProvider, FS fs, int tms) throws TransportException {
        if (myFactories.containsKey(uri)) {
          return myFactories.get(uri).getSession(uri, credentialsProvider, fs, tms);
        }
        throw new IllegalStateException("No session factory for " + uri + " available");
      }

      @Override
      public String getType() {
        return "TeamCityMetaFactory";
      }
    });
  }

  private static void configureClientVersion(@NotNull Session session) {
    String teamCityVersion = getTeamCityVersion();
    if (teamCityVersion != null) {
      session.setClientVersion(GitUtils.getSshClientVersion(session.getClientVersion(), teamCityVersion));
    }
  }

  @Nullable
  private static String getTeamCityVersion() {
    try {
      ServerVersionInfo version = ServerVersionHolder.getVersion();
      return "TeamCity Server " + version.getDisplayVersion();
    } catch (Exception e) {
      return null;
    }
  }

  @NotNull
  @Override
  public SshSessionFactory getSshSessionFactory(@NotNull URIish url, @NotNull AuthSettings authSettings) throws VcsException {
    switch (authSettings.getAuthMethod()) {
      case PRIVATE_KEY_DEFAULT:
        return new DefaultJschConfigSessionFactory(myConfig, authSettings, myJSchOptions);
      case PRIVATE_KEY_FILE:
        return new CustomPrivateKeySessionFactory(myConfig, authSettings, myJSchOptions);
      case TEAMCITY_SSH_KEY:
        return new TeamCitySshKeySessionFactory(myConfig, authSettings, myJSchOptions, mySshKeyManager);
      case PASSWORD:
        return new PasswordJschConfigSessionFactory(myConfig, authSettings, myJSchOptions);
      default:
        final AuthenticationMethod method = authSettings.getAuthMethod();
        final String methodName = method == null ? "<null>" : method.uiName();
        throw new VcsAuthenticationException(url.toString(), "The authentication method " + methodName + " is not supported for SSH, please provide SSH key or credentials");
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

  @Override
  public <T> T withSshSessionFactory(@NotNull URIish url, @NotNull AuthSettings authSettings, @NotNull Callable<T> operation) throws VcsException {
    myFactories.put(url, getSshSessionFactory(url, authSettings));
    try {
      return operation.call();
    } catch (Throwable t) {
      throw new VcsException(t);
    } finally {
      myFactories.remove(url);
    }
  }

  private static class DefaultJschConfigSessionFactory extends JschConfigSessionFactory {
    protected final ServerPluginConfig myConfig;
    protected final AuthSettings myAuthSettings;
    private final Map<String,String> myJschOptions;

    private DefaultJschConfigSessionFactory(@NotNull ServerPluginConfig config,
                                            @NotNull AuthSettings authSettings,
                                            @NotNull Map<String,String> jschOptions) {
      myConfig = config;
      myAuthSettings = authSettings;
      myJschOptions = jschOptions;
    }

    @Override
    protected void configure(OpenSshConfig.Host hc, Session session) {
      configureClientVersion(session);
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
                                             @NotNull AuthSettings authSettings,
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
                                           @NotNull AuthSettings authSettings,
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

    @Override
    protected void configure(OpenSshConfig.Host hc, Session session) {
      super.configure(hc, session);
      session.setConfig("StrictHostKeyChecking", "no");
    }
  }

  private static class TeamCitySshKeySessionFactory extends DefaultJschConfigSessionFactory {

    private final VcsRootSshKeyManager mySshKeyManager;

    private TeamCitySshKeySessionFactory(@NotNull ServerPluginConfig config,
                                         @NotNull AuthSettings authSettings,
                                         @NotNull Map<String,String> jschOptions,
                                         @NotNull VcsRootSshKeyManager sshKeyManager) {
      super(config, authSettings, jschOptions);
      mySshKeyManager = sshKeyManager;
    }

    @Override
    protected JSch getJSch(OpenSshConfig.Host hc, FS fs) throws JSchException {
      return createDefaultJSch(fs);
    }

    @Override
    protected JSch createDefaultJSch(FS fs) throws JSchException {
      final JSch jsch = new JSch();
      final VcsRoot root = myAuthSettings.getRoot();
      if (root != null) {
        TeamCitySshKey sshKey = mySshKeyManager.getKey(root);
        if (sshKey != null) {
          try {
            jsch.addIdentity("", sshKey.getPrivateKey(), null, myAuthSettings.getPassphrase() != null ? myAuthSettings.getPassphrase().getBytes() : null);
          } catch (JSchException e) {
            String keyName = root.getProperty(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME);
            if (keyName == null)
              throw e;
            throw new JSchException(getErrorMessage(keyName, e), e);
          }
        }
      }
      return jsch;
    }

    @NotNull
    private String getErrorMessage(@NotNull String keyName, @NotNull JSchException e) {
      String msg = e.getMessage();
      if (msg == null) {
        LOG.debug("Error while loading an uploaded key '" + keyName + "'", e);
        return "Error while loading an uploaded key '" + keyName + "'";
      }
      int idx = msg.indexOf("[B@");
      if (idx >= 0) {
        msg = msg.substring(0, idx);
        msg = msg.trim();
        if (msg.endsWith(":"))
          msg = msg.substring(0, msg.length() - 1);
      }
      return "Error while loading an uploaded key '" + keyName + "': " + msg;
    }

    @Override
    protected void configure(OpenSshConfig.Host hc, Session session) {
      super.configure(hc, session);
      session.setConfig("StrictHostKeyChecking", "no");
    }
  }
}
