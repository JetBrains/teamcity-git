package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.ProxyHandler;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.util.BaseGitTestCase;
import org.apache.http.auth.NTCredentials;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

public class ProxyTests extends BaseGitTestCase {

  @Test
  public void protocol_host_port_test() {
    setInternalProperty("teamcity.https.proxyHost", "0.0.0.0");
    setInternalProperty("teamcity.https.proxyPort", "4444");

    setInternalProperty("teamcity.http.proxyHost", "localhost");
    setInternalProperty("teamcity.http.proxyPort", "3333");

    ProxyHandler proxyHandler = new ProxyHandler();

    then(proxyHandler.getHttpsProxyHost()).isEqualTo("0.0.0.0");
    then(proxyHandler.getHttpProxyHost()).isEqualTo("localhost");
    then(proxyHandler.getHttpsProxyPort()).isEqualTo(4444);
    then(proxyHandler.getHttpProxyPort()).isEqualTo(3333);
  }

  @Test
  public void basic_auth_credentials_test(){
    setInternalProperty("teamcity.https.proxyHost", "localhost");
    setInternalProperty("teamcity.https.proxyPort", "3333");

    setInternalProperty("teamcity.http.proxyHost", "localhost");
    setInternalProperty("teamcity.http.proxyPort", "3333");

    setInternalProperty("teamcity.https.proxyLogin", "admin");
    setInternalProperty("teamcity.https.proxyPassword", "000");

    ProxyHandler proxyHandler = new ProxyHandler();

    then(proxyHandler.getHttpsCredentials().getUserPrincipal().getName()).isEqualTo("admin");
    then(proxyHandler.getHttpsCredentials().getPassword()).isEqualTo("000");


    setInternalProperty("teamcity.https.proxyAuthentication", "login:123");
    setInternalProperty("teamcity.http.proxyAuthentication", "user:456");
    proxyHandler = new ProxyHandler();

    then(proxyHandler.getHttpsCredentials().getUserPrincipal().getName()).isEqualTo("login");
    then(proxyHandler.getHttpsCredentials().getPassword()).isEqualTo("123");
    then(proxyHandler.getHttpCredentials().getUserPrincipal().getName()).isEqualTo("user");
    then(proxyHandler.getHttpCredentials().getPassword()).isEqualTo("456");
  }

  @Test
  public void ntlm_auth_credentials_test(){
    setInternalProperty("teamcity.https.proxyHost", "localhost");
    setInternalProperty("teamcity.https.proxyPort", "3333");
    setInternalProperty("teamcity.https.proxyAuthenticationType", "ntlm");


    setInternalProperty("teamcity.https.proxyAuthentication", "DOMAIN\\login:123");
    ProxyHandler proxyHandler = new ProxyHandler();

    then(proxyHandler.getHttpsCredentials().getClass()).isEqualTo(NTCredentials.class);
    then(proxyHandler.getHttpsCredentials().getUserPrincipal().getName()).isEqualTo("DOMAIN\\login");
    then(proxyHandler.getHttpsCredentials().getPassword()).isEqualTo("123");
  }
}
