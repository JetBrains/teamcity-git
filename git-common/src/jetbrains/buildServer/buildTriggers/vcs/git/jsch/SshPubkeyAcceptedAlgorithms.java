package jetbrains.buildServer.buildTriggers.vcs.git.jsch;

import com.jcraft.jsch.Session;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class SshPubkeyAcceptedAlgorithms {
  public static void configureSession(@NotNull Session session) {
    String sha1SignatureEnforcedDomains = TeamCityProperties.getProperty("teamcity.git.ssh.domainsWithEnforcedSha1Signature", ".azure.com,.visualstudio.com");
    String host = session.getHost();
    if (host != null) {
      boolean enforceSha1 = false;
      for (String domain: StringUtil.split(sha1SignatureEnforcedDomains, true, ',')) {
        final String d = domain.trim();
        if (host.contains(d) || d.equals("*")) {
          enforceSha1 = true;
          break;
        }
      }

      if (enforceSha1) {
        String pubKeyAlgs = session.getConfig("PubkeyAcceptedAlgorithms");
        if (!pubKeyAlgs.startsWith("ssh-rsa")) {
          pubKeyAlgs = "ssh-rsa," + pubKeyAlgs;
          session.setConfig("PubkeyAcceptedAlgorithms", pubKeyAlgs);
        }
      }
    }

  }
}
