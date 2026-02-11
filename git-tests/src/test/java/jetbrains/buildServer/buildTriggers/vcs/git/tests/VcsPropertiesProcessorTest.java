

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.Collections;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.util.InternalPropertiesHandler;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsException;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.HashMap;

import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.groups.Tuple.tuple;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

/**
 * @author dmitry.neverov
 */
@Test
public class VcsPropertiesProcessorTest extends TestCase {

  private VcsPropertiesProcessor myProcessor = new VcsPropertiesProcessor(new PluginConfigImpl());

  private InternalPropertiesHandler myInternalPropertiesHandler;

  @BeforeMethod
  @Override
  public void setUp() throws Exception {
    super.setUp();

    myInternalPropertiesHandler = new InternalPropertiesHandler();
  }

  @AfterMethod
  @Override
  public void tearDown() throws Exception {
    if (myInternalPropertiesHandler != null) {
      myInternalPropertiesHandler.tearDown();
    }

    super.tearDown();
  }

  public void empty_push_url_is_allowed() {
    Collection<InvalidProperty> invalids = myProcessor.process(new HashMap<String, String>() {{
      put(Constants.BRANCH_NAME, "refs/heads/master");
      put(Constants.FETCH_URL, "git://some.org/repository");
    }});
    assertTrue(invalids.isEmpty());
  }


  public void non_default_key_auth_requires_private_key_path() {
    Collection<InvalidProperty> invalids = myProcessor.process(new HashMap<String, String>() {{
      put(Constants.BRANCH_NAME, "refs/heads/master");
      put(Constants.FETCH_URL, "git://some.org/repository");
      put(Constants.AUTH_METHOD, "PRIVATE_KEY_FILE");
    }});
    assertEquals(1, invalids.size());
    assertEquals(Constants.PRIVATE_KEY_PATH, invalids.iterator().next().getPropertyName());
  }


  public void branch_starts_with_slash() {
    Collection<InvalidProperty> invalids = myProcessor.process(
      map(Constants.FETCH_URL, "git://some.org/repository",
          Constants.BRANCH_NAME, "/refs/heads/master",
          Constants.BRANCH_SPEC, "+:refs/heads/*"));
    assertEquals(1, invalids.size());
    assertEquals(Constants.BRANCH_NAME, invalids.iterator().next().getPropertyName());
  }


  public void branchSpec_contains_pattern_started_from_slash() {
    Collection<InvalidProperty> invalids = myProcessor.process(
      map(Constants.FETCH_URL, "git://some.org/repository",
          Constants.BRANCH_NAME, "refs/heads/master",
          Constants.BRANCH_SPEC, "+:/refs/heads/*"));
    assertEquals(1, invalids.size());
    assertEquals(Constants.BRANCH_SPEC, invalids.iterator().next().getPropertyName());
  }


  @TestFor(issues = "TW-30747")
  public void branchSpec_no_plus_pattern_started_from_slash() {
    Collection<InvalidProperty> invalids = myProcessor.process(
      map(Constants.FETCH_URL, "git://some.org/repository",
          Constants.BRANCH_NAME, "refs/heads/master",
          Constants.BRANCH_SPEC, "/refs/heads/*"));
    assertEquals(1, invalids.size());
    assertEquals(Constants.BRANCH_SPEC, invalids.iterator().next().getPropertyName());
  }


  @TestFor(issues = "TW-23174")
  public void branchSpec_count_empty_lines() {
    Collection<InvalidProperty> invalids = myProcessor.process(
      map(Constants.FETCH_URL, "git://some.org/repository",
          Constants.BRANCH_NAME, "refs/heads/master",
          Constants.BRANCH_SPEC, "+:refs/heads/*\n\n-:/refs/heads/release"));
    assertEquals(1, invalids.size());
    String reason = invalids.iterator().next().getInvalidReason();
    assertTrue(reason != null && reason.startsWith("Line 3:"));
  }


  @TestFor(issues = {"TW-23424", "TW-23220"})
  public void should_not_try_to_parse_urls_with_parameters() {
    Collection<InvalidProperty> errors = myProcessor.process(map(Constants.FETCH_URL, "file://c:%5CWork\\Test\\git\\%url.param%",
                                                                 Constants.PUSH_URL, "file://c:%5CWork\\Test\\git\\%url.param%",
                                                                 Constants.BRANCH_NAME, "refs/heads/master"));
    assertTrue(errors.isEmpty());
  }


  @TestFor(issues = "TW-46315")
  public void prohibit_empty_default_branch() {
    Collection<InvalidProperty> errors = myProcessor.process(map(Constants.FETCH_URL, "git://some.org/repository"));
    then(errors).extracting("propertyName", "invalidReason")
      .contains(tuple(Constants.BRANCH_NAME, "Branch name must be specified"));
  }


  @TestFor(issues = "TW-50043")
  public void prohibit_newline_in_urls() {
    Collection<InvalidProperty> errors = myProcessor.process(map(
      Constants.FETCH_URL, "git://some.org/repository\n",
      Constants.BRANCH_NAME, "refs/heads/master"));
    then(errors).extracting("propertyName").contains(Constants.FETCH_URL);
    errors = myProcessor.process(map(
      Constants.FETCH_URL, "git://some.org/repository\r",
      Constants.BRANCH_NAME, "refs/heads/master"));
    then(errors).extracting("propertyName").contains(Constants.FETCH_URL);

    errors = myProcessor.process(map(
      Constants.FETCH_URL, "git://some.org/repository",
      Constants.PUSH_URL, "git://some.org/repository\n",
      Constants.BRANCH_NAME, "refs/heads/master"));
    then(errors).extracting("propertyName").contains(Constants.PUSH_URL);
    errors = myProcessor.process(map(
      Constants.FETCH_URL, "git://some.org/repository",
      Constants.PUSH_URL, "git://some.org/repository\r",
      Constants.BRANCH_NAME, "refs/heads/master"));
    then(errors).extracting("propertyName").contains(Constants.PUSH_URL);
  }

  @DataProvider
  public static Object[][] urlsWithIncompatibleAuthType() {
    return new Object[][]{
      new Object[] { "http://some.org/repo", AuthenticationMethod.PRIVATE_KEY_DEFAULT},
      new Object[] { "http://some.org/repo", AuthenticationMethod.PRIVATE_KEY_FILE},
      new Object[] { "http://some.org/repo", AuthenticationMethod.TEAMCITY_SSH_KEY},
      new Object[] { "https://some.org/repo", AuthenticationMethod.PRIVATE_KEY_DEFAULT},
      new Object[] { "ssh://some.org/repo", AuthenticationMethod.PASSWORD},
      new Object[] { "git@github.com:org/repo", AuthenticationMethod.PASSWORD},
      new Object[] { "user@github.com:org/repo", AuthenticationMethod.PASSWORD},
      new Object[] { "git@github.com:org/repo", AuthenticationMethod.ANONYMOUS},
      new Object[] { "git@github.com:org/repo", AuthenticationMethod.ACCESS_TOKEN},
    };
  }

  @TestFor(issues = "TW-82895")
  @Test(dataProvider = "urlsWithIncompatibleAuthType")
  public void incompatible_auth_type(@NotNull String incompatibleUrl, @NotNull AuthenticationMethod authMethod) {
    try {
      VcsPropertiesProcessor.validateUrlAuthMethod(incompatibleUrl, authMethod, "fetch");
      fail("No error for fetch url '" + incompatibleUrl + "'");
    } catch (VcsException e) {
      assertTrue(e.getMessage().endsWith("protocol of the fetch url"));
    }
  }

  @DataProvider
  public static Object[][] urlsWithCompatibleType() {
    return new Object[][]{
      new Object[] { "ssh://some.org/repo", AuthenticationMethod.PRIVATE_KEY_DEFAULT},
      new Object[] { "ssh://some.org/repo", AuthenticationMethod.PRIVATE_KEY_FILE},
      new Object[] { "ssh://some.org/repo", AuthenticationMethod.TEAMCITY_SSH_KEY},
      new Object[] { "git@server.org:project.git", AuthenticationMethod.TEAMCITY_SSH_KEY},
      new Object[] { "user@server.org:project", AuthenticationMethod.TEAMCITY_SSH_KEY},
      new Object[] { "user@server.org:some_org/project.git", AuthenticationMethod.TEAMCITY_SSH_KEY},
      new Object[] { "some.org/project.git", AuthenticationMethod.ACCESS_TOKEN},
      new Object[] { "some.org/project.git", AuthenticationMethod.PASSWORD},
      new Object[] { "https://some.org/project.git", AuthenticationMethod.PASSWORD},
      new Object[] { "http://some.org/project.git", AuthenticationMethod.ACCESS_TOKEN},
      new Object[] { "http://some.org/project.git", AuthenticationMethod.ANONYMOUS},
    };
  }

  @TestFor(issues = "TW-82895")
  @Test(dataProvider = "urlsWithCompatibleType")
  public void compatible_auth_type(@NotNull String url, @NotNull AuthenticationMethod authMethod) {
    try {
      VcsPropertiesProcessor.validateUrlAuthMethod(url, authMethod, "fetch");
    } catch (VcsException e) {
      fail("Unexpected error for url '" + url + "'");
    }
  }

  @TestFor(issues = "TW-95933")
  @Test
  public void testLocalFileFetchUrlIsBlocked() {
    myInternalPropertiesHandler.setInternalProperty("teamcity.git.allowFileUrl", "false");
    final Map<String, String> props = ImmutableMap.of(
      "branch", "refs/heads/main",
      "url", "file:///tmp/testrepo.git"
    );

    final Collection<InvalidProperty> invalidProps = myProcessor.process(props);

    then(invalidProps).extracting("propertyName", "invalidReason")
                      .containsExactly(tuple("url", "The URL must not be a local file URL"));
  }

  @TestFor(issues = "TW-95933")
  @Test
  public void testLocalFilePushUrlIsBlocked() {
    myInternalPropertiesHandler.setInternalProperty("teamcity.git.allowFileUrl", "false");
    final Map<String, String> props = ImmutableMap.of(
      "branch", "refs/heads/main",
      "url", "https://my.git.test/testrepo.git",
      "push_url", "file:///tmp/testrepo.git"
    );

    final Collection<InvalidProperty> invalidProps = myProcessor.process(props);

    then(invalidProps).extracting("propertyName", "invalidReason")
                      .containsExactly(tuple("push_url", "The URL must not be a local file URL"));
  }


  @TestFor(issues = "TW-95933")
  @Test
  public void testLocalFileFetchUrlIsNotBlockedByDefault() {
    final Map<String, String> props = ImmutableMap.of(
      "branch", "refs/heads/main",
      "url", "file:///tmp/testrepo.git"
    );

    final Collection<InvalidProperty> invalidProps = myProcessor.process(props);

    then(invalidProps).isEmpty();
  }
}