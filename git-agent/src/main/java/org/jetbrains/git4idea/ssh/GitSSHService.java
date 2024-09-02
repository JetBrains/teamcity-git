

package org.jetbrains.git4idea.ssh;

import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The provider of SSH scripts for the Git
 */
public abstract class GitSSHService {

  /**
   * Path to the generated script
   */
  protected File myScript;
  protected String myScriptPath;

  /**
   * @return the port number for XML RCP
   */
  public abstract int getXmlRcpPort();

  /**
   * Get file to the script service
   *
   * @return path to the script
   * @throws IOException if script cannot be generated
   */
  @NotNull
  public abstract String getScriptPath() throws IOException;

  /**
   * @return the temporary directory to use or null if the default directory might be used
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  @Nullable
  protected File getTempDir() {
    return null;
  }


  /**
   * Handler interface to use by the client code
   */
  public interface Handler {

  }

}