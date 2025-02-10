package jetbrains.buildServer.buildTriggers.vcs.git.tests.util;

import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public class BaseGitServerTestCase extends BaseServerTestCase {
  private InternalPropertiesHandler myInternalPropertiesHandler;

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    myInternalPropertiesHandler = new InternalPropertiesHandler();
    super.setUp();
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
