

package org.jetbrains.git4idea.ssh;

import org.jetbrains.annotations.NonNls;

/**
 * An interface for GIT SSH handler
 */
public interface GitSSHHandler {
  /**
   * The prefix of the ssh script name
   */
  @NonNls String GIT_SSH_PREFIX = "git-ssh-";
  /**
   * Name of environment variable for SSH handler number
   */
  @NonNls String SSH_IGNORE_KNOWN_HOSTS_ENV = "GIT4IDEA_SSH_IGNORE_KNOWN_HOSTS";
  @NonNls String SSH_KNOWN_HOSTS_FILE = "GIT4IDEA_SSH_KNOWN_HOSTS_FILE";
  /**
   * Name of environment variable for SSH handler
   */
  @NonNls String SSH_PORT_ENV = "GIT4IDEA_SSH_PORT";
  /**
   * Name of environment variable for SSH executable
   */
  @NonNls String GIT_SSH_ENV = "GIT_SSH";
  @NonNls String GIT_SSH_COMMAND_ENV = "GIT_SSH_COMMAND";
  String GIT_SSH_VARIANT_ENV = "GIT_SSH_VARIANT";

  String TEAMCITY_PRIVATE_KEY_PATH = "TEAMCITY_PRIVATE_KEY";
  String TEAMCITY_PASSPHRASE = "TEAMCITY_PASSPHRASE";
  String TEAMCITY_DEBUG_SSH = "TEAMCITY_DEBUG_SSH";
  String TEAMCITY_SSH_MAC_TYPE = "TEAMCITY_SSH_MAC_TYPE";
  String TEAMCITY_SSH_PREFERRED_AUTH_METHODS = "TEAMCITY_SSH_PREFERRED_AUTH_METHODS";
  String TEAMCITY_SSH_IDLE_TIMEOUT_SECONDS = "TEAMCITY_SSH_IDLE_TIMEOUT_SECONDS";
  String TEAMCITY_SSH_CONNECT_TIMEOUT_SECONDS = "TEAMCITY_SSH_CONNECT_TIMEOUT_SECONDS";
  String TEAMCITY_VERSION = "TEAMCITY_VERSION";
  String TEAMCITY_SSH_REQUEST_TOKEN = "TEAMCITY_SSH_REQUEST_TOKEN";
  String TEAMCITY_INT_PROPS_PATH = "TEAMCITY_INT_PROPS_PATH";

}