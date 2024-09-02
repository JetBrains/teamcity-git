package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.Properties;
import jetbrains.buildServer.buildTriggers.vcs.git.GitURLMapper;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

public class GitUrlMapperTest {
  private Properties myPropertiesBeforeTest;
  private static String PROPERTY_PREFIX = "temcity.gitUrlMapper.property.prefix";


  @BeforeMethod
  void setUp() {
    myPropertiesBeforeTest = GitTestUtil.copyCurrentProperties();
  }

  @AfterMethod
  void tearDown() {
    GitTestUtil.restoreProperties(myPropertiesBeforeTest);
  }

  @Test
  public void should_modify_only_url_matched_by_simple_rule() {
    System.setProperty(getPropName(PROPERTY_PREFIX, 0), "https://github.com/user/test => http://localhost:8123/test");
    then(GitURLMapper.getModifiedURL("https://github.com/user/test", PROPERTY_PREFIX)).isEqualTo("http://localhost:8123/test");
    then(GitURLMapper.getModifiedURL("https://github.com/user/test1", PROPERTY_PREFIX)).isNull();
  }

  @Test
  public void should_modify_only_url_matched_by_wildcard_rule() {
    System.setProperty(getPropName(PROPERTY_PREFIX, 0), "https://github.com/user/* => http://localhost:8123/");
    then(GitURLMapper.getModifiedURL("https://github.com/user/test", PROPERTY_PREFIX)).isEqualTo("http://localhost:8123/test");
    then(GitURLMapper.getModifiedURL("https://github.com/user/", PROPERTY_PREFIX)).isEqualTo("http://localhost:8123/");
    then(GitURLMapper.getModifiedURL("https://github.com/user1/test", PROPERTY_PREFIX)).isEqualTo(null);
  }

  @Test
  public void should_return_null_if_rules_are_incorrect() {
    System.setProperty(getPropName(PROPERTY_PREFIX, 0), "https://github.com/user/* => http://localhost:8123/ => test");
    then(GitURLMapper.getModifiedURL("https://github.com/user/test", PROPERTY_PREFIX)).isEqualTo(null);
  }

  private String  getPropName(String prefix, int i) {
    return prefix + "." + i;
  }
}
