package jetbrains.buildServer.buildTriggers.vcs.git.tests.util;

import jetbrains.buildServer.vcs.patches.PatchTestCase;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public class BaseGitPatchTestCase extends PatchTestCase {
  private InternalPropertiesHandler myInternalPropertiesHandler;

  @BeforeMethod(alwaysRun = true)
  protected void setUp() throws Exception {
    myInternalPropertiesHandler = new InternalPropertiesHandler();
    super.setUp();
  }

  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
    myInternalPropertiesHandler.tearDown();
  }

  protected void setInternalProperty(@NotNull String propKey, @NotNull String value) {
    myInternalPropertiesHandler.setInternalProperty(propKey, value);
  }
}
