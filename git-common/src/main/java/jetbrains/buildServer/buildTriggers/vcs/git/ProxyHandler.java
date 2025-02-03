package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProxyHandler {

  private static final String PROXY_COMMAND = "teamcity.git.native.sshProxyCmd";
  private static final String ENABLED_PARAM_NAME = "teamcity.git.propagateProxy";

  @Nullable
  private final String myHttpProxyHost;
  private final int myHttpProxyPort;
  @Nullable
  private final String myHttpsProxyHost;
  private final int myHttpsProxyPort;
  @Nullable
  private final String myNonProxyHosts;
  @Nullable
  private final Credentials myHttpCredentials;
  @Nullable
  private final Credentials myHttpsCredentials;
  @Nullable
  private final SshProxyType mySshProxyType;
  @Nullable
  private final String mySshProxyHost;
  private final int mySshProxyPort;

  public ProxyHandler() {
    myHttpProxyHost = getString("http.proxyHost");
    myHttpProxyPort = getInt("http.proxyPort");
    myHttpsProxyHost = getString("https.proxyHost");
    myHttpsProxyPort = getInt("https.proxyPort");
    String nonProxyHosts = getString("http.nonProxyHosts");
    myNonProxyHosts =
      nonProxyHosts != null ? nonProxyHosts : getString("https.nonProxyHosts"); // handle this property name because it was mentioned in tc documentation, but doesn't exist in java

    myHttpCredentials = myHttpProxyHost == null ? null : getCredentials("http");
    myHttpsCredentials = myHttpsProxyHost == null ? null : getCredentials("https");

    String sshType = getString("git.sshProxyType");
    if (sshType != null) {
      switch (sshType) {
        case "socks5":
          mySshProxyType = SshProxyType.SOCKS5;
          break;
        case "socks4":
          mySshProxyType = SshProxyType.SOCKS4;
          break;
        case "http":
          mySshProxyType = SshProxyType.HTTP;
          break;
        default:
          mySshProxyType = null;
      }
    } else {
      mySshProxyType = null;
    }
    mySshProxyHost = getString("git.sshProxyHost");
    mySshProxyPort = getInt("git.sshProxyPort");
  }

  public enum SshProxyType {
    HTTP,
    SOCKS4,
    SOCKS5
  }

  public boolean isHttpProxyEnabled() {
    return isEnabled() && myHttpProxyHost != null;
  }

  public boolean isHttpsProxyEnabled() {
    return isEnabled() && myHttpsProxyHost != null;
  }

  public boolean isSshProxyEnabled() {
    return isEnabled() && mySshProxyHost != null && mySshProxyType != null;
  }

  @Nullable
  public String getHttpProxyHost() {
    return myHttpProxyHost;
  }

  public int getHttpProxyPort() {
    return myHttpProxyPort;
  }

  @Nullable
  public String getHttpsProxyHost() {
    return myHttpsProxyHost;
  }

  public int getHttpsProxyPort() {
    return myHttpsProxyPort;
  }

  @Nullable
  public String getNonProxyHosts() {
    return myNonProxyHosts;
  }

  @Nullable
  public Credentials getHttpCredentials() {
    return myHttpCredentials;
  }

  @Nullable
  public Credentials getHttpsCredentials() {
    return myHttpsCredentials;
  }

  @Nullable
  public SshProxyType getSshProxyType() {
    return mySshProxyType;
  }

  @Nullable
  public String getSshProxyHost() {
    return mySshProxyHost;
  }

  @Nullable
  public String getCustomSshProxyCommand() {
    return TeamCityProperties.getPropertyOrNull(PROXY_COMMAND);
  }

  public int getSshProxyPort() {
    return mySshProxyPort;
  }

  private Credentials getCredentials(String proto) {
    final String auth = getString(proto + ".proxyAuthentication");
    final String username = getString(proto + ".proxyLogin");
    final String password = getString(proto + ".proxyPassword");
    String authType = getString(proto + ".proxyAuthenticationType");
    if (authType == null) {
      authType = "basic";
    }

    Credentials credentials = null;
    // other authentication types can be supported,
    // but currently authentication is handled the same way as in HttpClientConfigurator
    switch (authType.toLowerCase()) {
      case "ntlm":
        if (auth != null) {
          credentials = new NTCredentials(auth);
        }
        break;
      default:
        if (auth != null) {
          credentials = new UsernamePasswordCredentials(auth);
        } else if (username != null) {
          credentials = new UsernamePasswordCredentials(username, password);
        }
        break;
    }

    return credentials;
  }

  @Nullable
  private String getString(@NotNull String propName) {
    return TeamCityProperties.getPropertyOrNull("teamcity." + propName);
  }

  private int getInt(@NotNull String propName) {
    return TeamCityProperties.getInteger("teamcity." + propName, 0);
  }

  private boolean isEnabled() {
    return TeamCityProperties.getBooleanOrTrue(ENABLED_PARAM_NAME);
  }
}
