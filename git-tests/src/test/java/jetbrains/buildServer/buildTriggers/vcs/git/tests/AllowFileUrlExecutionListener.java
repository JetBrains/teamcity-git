package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import org.testng.IExecutionListener;

/**
 * TestNG listener to always enable file URL access for tests as an internal property.
 *
 * @since 2026.1
 * @see resources/META-INF/services/org.testng.IExecutionListener
 */
public class AllowFileUrlExecutionListener implements IExecutionListener {
  @Override
  public void onExecutionStart() {
    System.setProperty(Constants.ALLOW_FILE_URL, "true");
  }

  @Override
  public void onExecutionFinish() {}
}
