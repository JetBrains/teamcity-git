package jetbrains.buildServer.buildTriggers.vcs.git.tests.util;

import jetbrains.buildServer.BaseTestCase;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public class BaseGitTestCase extends BaseTestCase {

  private InternalPropertiesHandler myInternalPropertiesHandler = new InternalPropertiesHandler();

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myInternalPropertiesHandler = new InternalPropertiesHandler();
  }

  @AfterMethod
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    myInternalPropertiesHandler.tearDown();
  }

  @Override
  protected void setInternalProperty(@NotNull String propKey, @NotNull String value) {
    myInternalPropertiesHandler.setInternalProperty(propKey, value);
  }
}
