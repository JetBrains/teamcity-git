package jetbrains.buildServer.buildTriggers.vcs.git.tests.util;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public class BaseSimpleGitTestCase {

  private InternalPropertiesHandler myInternalPropertiesHandler;

  @BeforeMethod(alwaysRun = true)
  protected void setUp() throws Exception {
    myInternalPropertiesHandler = new InternalPropertiesHandler();
  }

  @AfterMethod
  public void tearDown() throws Exception {
    myInternalPropertiesHandler.tearDown();
  }

  protected void setInternalProperty(@NotNull String propKey, @NotNull String value) {
    myInternalPropertiesHandler.setInternalProperty(propKey, value);
  }
}
