

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.Context;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.version.ServerVersionHolder;
import jetbrains.buildServer.version.ServerVersionInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.GitSSHService;

/**
 * SSH handler implementation
 */
public class SshHandler implements GitSSHService.Handler {
  /**
   * SSH service
   */
  private final GitSSHService mySsh;
  private final AuthSettings myAuthSettings;
  private final List<File> myFilesToClean = new ArrayList<File>();

  /**
   * The constructor that registers the handler in the SSH service and command line
   *
   * @param ssh the SSH service
   * @param authSettings authentication settings
   * @param cmd the command line to register with
   * @throws VcsException if there is a problem with registering the handler
   */
  public SshHandler(@NotNull GitSSHService ssh,
                    @Nullable VcsRootSshKeyManager sshKeyManager,
                    @NotNull AuthSettings authSettings,
                    @NotNull AgentGitCommandLine cmd,
                    @NotNull Context ctx) throws VcsException {
    mySsh = ssh;
    myAuthSettings = authSettings;
    cmd.addEnvParam(GitSSHHandler.SSH_PORT_ENV, Integer.toString(mySsh.getXmlRcpPort()));
    if (myAuthSettings.isIgnoreKnownHosts() && ctx.sshIgnoreKnownHosts()) {
      cmd.addEnvParam(GitSSHHandler.SSH_IGNORE_KNOWN_HOSTS_ENV, "true");
    } else {
      String knownHostsFromParam = ctx.getSshKnownHosts();
      if (knownHostsFromParam != null) {
        try {
          File knownHostsFile = FileUtil.createTempFile(ctx.getTempDir(), "known_hosts", "", true);
          try (FileWriter writer = new FileWriter(knownHostsFile)) {
            writer.write(knownHostsFromParam);
          }
          cmd.addEnvParam(GitSSHHandler.SSH_KNOWN_HOSTS_FILE, knownHostsFile.getAbsolutePath());
          myFilesToClean.add(knownHostsFile);
        } catch (IOException e) {
          throw new VcsException("SSH script cannot be generated: " + e.getMessage(), e);
        }

      }
    }
    if (authSettings.getAuthMethod() == AuthenticationMethod.TEAMCITY_SSH_KEY) {
      String keyId = authSettings.getTeamCitySshKeyId();
      if (keyId != null && sshKeyManager != null) {
        VcsRoot root = myAuthSettings.getRoot();
        if (root != null) {
          TeamCitySshKey key = sshKeyManager.getKey(root);
          if (key != null) {
            try {
              File privateKey = FileUtil.createTempFile(ctx.getTempDir(), "key", "", true);
              myFilesToClean.add(privateKey);
              FileUtil.writeFileAndReportErrors(privateKey, new String(key.getPrivateKey()));
              cmd.addEnvParam(GitSSHHandler.TEAMCITY_PRIVATE_KEY_PATH, privateKey.getCanonicalPath());
              String passphrase = myAuthSettings.getPassphrase();
              cmd.addEnvParam(GitSSHHandler.TEAMCITY_PASSPHRASE, passphrase != null ? passphrase : "");
            } catch (Exception e) {
              deleteKeys();
              throw new VcsException(e);
            }
          }
        }
      }
    }
    if (ctx.getSshMacType() != null)
      cmd.addEnvParam(GitSSHHandler.TEAMCITY_SSH_MAC_TYPE, ctx.getSshMacType());
    if (ctx.getPreferredSshAuthMethods() != null)
      cmd.addEnvParam(GitSSHHandler.TEAMCITY_SSH_PREFERRED_AUTH_METHODS, ctx.getPreferredSshAuthMethods());
    cmd.addEnvParam(GitSSHHandler.TEAMCITY_DEBUG_SSH, String.valueOf(ctx.isDebugSsh()));
    cmd.addEnvParam(GitSSHHandler.TEAMCITY_SSH_IDLE_TIMEOUT_SECONDS, String.valueOf(ctx.getIdleTimeoutSeconds()));
    cmd.addEnvParam(GitSSHHandler.TEAMCITY_SSH_CONNECT_TIMEOUT_SECONDS, String.valueOf(ctx.getSshConnectTimeoutSeconds()));
    String teamCityVersion = getTeamCityVersion();
    if (teamCityVersion != null) {
      cmd.addEnvParam(GitSSHHandler.TEAMCITY_VERSION, teamCityVersion);
    }

    try {
      File intPropsFile = FileUtil.createTempFile(ctx.getTempDir(), "int_props", "", true);
      Properties props = new Properties();
      props.putAll(TeamCityProperties.getPropertiesWithPrefix("teamcity."));
      try (FileWriter fw = new FileWriter(intPropsFile)) {
        props.store(fw, "Internal properties");
      }
      cmd.addEnvParam(GitSSHHandler.TEAMCITY_INT_PROPS_PATH, intPropsFile.getAbsolutePath());
      myFilesToClean.add(intPropsFile);
    } catch (IOException e) {
      //
    }

    try {
      cmd.addEnvParam(GitSSHHandler.GIT_SSH_ENV, ssh.getScriptPath());
      // ask git to treat our command as OpenSSH compatible:
      cmd.addEnvParam(GitSSHHandler.GIT_SSH_VARIANT_ENV, "ssh");
    } catch (IOException e) {
      deleteKeys();
      throw new VcsException("SSH script cannot be generated: " + e.getMessage(), e);
    }

    final String sendEnv = ctx.getSshRequestToken();
    if (StringUtil.isNotEmpty(sendEnv)) {
      cmd.addEnvParam(GitSSHHandler.TEAMCITY_SSH_REQUEST_TOKEN, sendEnv);
    }
  }

  /**
   * Unregister the handler
   */
  public void unregister() {
    deleteKeys();
  }


  private void deleteKeys() {
    for (File f : myFilesToClean) {
      FileUtil.delete(f);
    }
  }

  @Nullable
  private static String getTeamCityVersion() {
    try {
      ServerVersionInfo version = ServerVersionHolder.getVersion();
      return "TeamCity Agent " + version.getDisplayVersion();
    } catch (Exception e) {
      return null;
    }
  }
}