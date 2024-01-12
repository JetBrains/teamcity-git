

package jetbrains.buildServer.buildTriggers.vcs.git;

/**
 * Authentication method
 */
public enum AuthenticationMethod {
  /**
   * Anonymous access (or password is a part of URL)
   */
  ANONYMOUS("Anonymous", false, false),
  /**
   * The default SSH private key
   */
  PRIVATE_KEY_DEFAULT("Default Private Key", true, false),
  /**
   * The private key is specified in the file
   */
  PRIVATE_KEY_FILE("Private Key", true, false),
  /**
   * The password is used
   */
  PASSWORD("Password", false, true),

  /**
   * Access token
   */
  ACCESS_TOKEN("Access Token", false, true),

  TEAMCITY_SSH_KEY("TeamCity SSH Key", true, false);

  /**
   * Name of auth method for user, e.g. in error messages
   */
  private final String myUIName;
  private final boolean myIsSsh;
  private final boolean myIsPasswordBased;

  AuthenticationMethod(String uiName, final boolean isSsh, final boolean isPasswordBased) {
    myUIName = uiName;
    myIsSsh = isSsh;
    myIsPasswordBased = isPasswordBased;
  }

  public String uiName() {
    return myUIName;
  }

  public boolean isKeyAuth() {
    return myIsSsh;
  }

  public boolean isPasswordBased() { return myIsPasswordBased; }
}