

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import org.testng.annotations.DataProvider;

/**
 * DataProvider for tests that should be run in both modes: fetch in same process and fetch in separate process
 * @author dmitry.neverov
 */
public class FetchOptionsDataProvider {
  @DataProvider(name = "doFetchInSeparateProcess")
  public static Object[][] createData() {
    return new Object[][] {
      new Object[] { Boolean.TRUE },
      new Object[] { Boolean.FALSE }
    };
  }
}