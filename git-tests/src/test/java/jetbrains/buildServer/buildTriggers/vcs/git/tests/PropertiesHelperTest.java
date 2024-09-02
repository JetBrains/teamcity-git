package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.PropertiesHelper;
import org.testng.Assert;
import org.testng.annotations.Test;


@Test
public class PropertiesHelperTest {

  @Test
  public static void alias_aggregate_empty_alias_test() {
    Map<String, String> properties = new HashMap<String, String>() {{
      put("teamcity.param", "A");
    }};

    Map<String, Map<String, String>> result = PropertiesHelper.aggregatePropertiesByAlias(properties, "teamcity.param");
    Assert.assertEquals(result.size(), 1);
    Map<String, String> params = result.get("");
    Assert.assertNotNull(params);
    Assert.assertEquals(params.get("teamcity.param"), "A");
  }

  @Test
  public static void alias_aggregate_ends_by_alias_test() {
    Map<String, String> properties = new HashMap<String, String>() {{
      put("teamcity.param.notalias", "A");
    }};

    Map<String, Map<String, String>> result = PropertiesHelper.aggregatePropertiesByAlias(properties, "teamcity.param");
    Assert.assertEquals(result.size(), 1);
    Map<String, String> params = result.get("");
    Assert.assertNotNull(params);
    Assert.assertEquals(params.get("teamcity.param.notalias"), "A");
  }

  @Test
  public static void alias_aggregate_ends_by_suffix_test() {
    Map<String, String> properties = new HashMap<String, String>() {{
      put("teamcity.param.alias.suffix", "A");
    }};

    Map<String, Map<String, String>> result = PropertiesHelper.aggregatePropertiesByAlias(properties, "teamcity.param");
    Assert.assertEquals(result.size(), 1);
    Map<String, String> params = result.get("alias");
    Assert.assertNotNull(params);
    Assert.assertEquals(params.get("teamcity.param.suffix"), "A");
  }

  @Test
  public static void alias_aggregate_no_prefix() {
    Map<String, String> properties = new HashMap<String, String>() {{
      put("teamcity.param.alias.suffix", "A");
    }};

    Map<String, Map<String, String>> result = PropertiesHelper.aggregatePropertiesByAlias(properties, "    ");
    Assert.assertTrue(result.isEmpty());
  }

  @Test
  public static void alias_aggregate_multiple_parameters_test() {
    Map<String, String> properties = new HashMap<String, String>() {{
      put("teamcity.cred.server1.url", "https://aaa1.bbb");
      put("teamcity.cred.server1.username", "admin1");
      put("teamcity.cred.server1.password", "pw1");
      put("teamcity.cred.server2.url", "https://aaa2.bbb");
      put("teamcity.cred.server2.username", "admin2");
      put("teamcity.cred.server2.password", "pw2");
      put("teamcity.cred.url", "https://aaa0.bbb");
      put("teamcity.cred.username", "admin0");
      put("teamcity.cred.password", "pw0");
      put("teamcity.cred.server3.url.ssh", "git@aaa.bb:xxx/yy.git");
    }};

    Map<String, Map<String, String>> result = PropertiesHelper.aggregatePropertiesByAlias(properties, "teamcity.cred");

    Assert.assertEquals(result.size(), 4);
    assert_multiple_result(result);
  }

  @Test
  public static void alias_aggregate_multiple_parameters_test_dot() {
    Map<String, String> properties = new HashMap<String, String>() {{
      put("teamcity.cred.server1.url", "https://aaa1.bbb");
      put("teamcity.cred.server1.username", "admin1");
      put("teamcity.cred.server1.password", "pw1");
      put("teamcity.cred.server2.url", "https://aaa2.bbb");
      put("teamcity.cred.server2.username", "admin2");
      put("teamcity.cred.server2.password", "pw2");
      put("teamcity.cred.url", "https://aaa0.bbb");
      put("teamcity.cred.username", "admin0");
      put("teamcity.cred.password", "pw0");
      put("teamcity.cred.server3.url.ssh", "git@aaa.bb:xxx/yy.git");
    }};

    Map<String, Map<String, String>> result = PropertiesHelper.aggregatePropertiesByAlias(properties, "teamcity.cred.");

    Assert.assertEquals(result.size(), 4);
    assert_multiple_result(result);
  }

  @Test
  public static void alias_aggregate_multiple_parameters_test_non_valid_props() {
    Map<String, String> properties = new HashMap<String, String>() {{
      put("teamcity.cred.server1.url", "https://aaa1.bbb");
      put("teamcity.cred.server1.username", "admin1");
      put("teamcity.cred.server1.password", "pw1");
      put("teamcity.cred.server2.url", "https://aaa2.bbb");
      put("teamcity.cred.server2.username", "admin2");
      put("teamcity.cred.server2.password", "pw2");
      put("teamcity.cred.url", "https://aaa0.bbb");
      put("teamcity.cred.username", "admin0");
      put("teamcity.cred.password", "pw0");
      put("teamcity.cred.server3.url.ssh", "git@aaa.bb:xxx/yy.git");

      put("teamcity.cred.", "123");
      put("teamcity.cred.alias.", "123");
      put("teamcity.cred.alias2.something.", "123");
    }};

    Map<String, Map<String, String>> result = PropertiesHelper.aggregatePropertiesByAlias(properties, "teamcity.cred.");

    Assert.assertEquals(result.size(), 5);
    assert_multiple_result(result);

    Assert.assertEquals(result.get("alias2").get("teamcity.cred.something."), "123");
  }

  private static void assert_multiple_result(Map<String, Map<String, String>> result) {
    Assert.assertNotNull(result.get(""));
    Assert.assertNotNull(result.get("server1"));
    Assert.assertNotNull(result.get("server2"));
    Assert.assertNotNull(result.get("server3"));


    Assert.assertFalse(
      Maps.difference(new HashMap<String, String>() {{
        put("teamcity.cred.url", "https://aaa0.bbb");
        put("teamcity.cred.username", "admin0");
        put("teamcity.cred.password", "pw0");
        put("something_to_verify_test_is_correct", "aaaaaaaaaaa");
      }}, result.get("")).areEqual()
    );

    Assert.assertTrue(
      Maps.difference(new HashMap<String, String>() {{
        put("teamcity.cred.url", "https://aaa0.bbb");
        put("teamcity.cred.username", "admin0");
        put("teamcity.cred.password", "pw0");
      }}, result.get("")).areEqual()
    );

    Assert.assertTrue(
      Maps.difference(new HashMap<String, String>() {{
        put("teamcity.cred.url", "https://aaa1.bbb");
        put("teamcity.cred.username", "admin1");
        put("teamcity.cred.password", "pw1");
      }}, result.get("server1")).areEqual()
    );

    Assert.assertTrue(
      Maps.difference(new HashMap<String, String>() {{
        put("teamcity.cred.url", "https://aaa2.bbb");
        put("teamcity.cred.username", "admin2");
        put("teamcity.cred.password", "pw2");
      }}, result.get("server2")).areEqual()
    );

    Assert.assertEquals(result.get("server3").size(), 1);
    Assert.assertEquals(result.get("server3").get("teamcity.cred.url.ssh"), "git@aaa.bb:xxx/yy.git");
  }
}
