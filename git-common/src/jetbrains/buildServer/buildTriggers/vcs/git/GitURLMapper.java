package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A mapping rule can look like this: https://example.com/org/test.git => http://proxy.com/test.git. In this case the whole url will be replaced
 * <br>
 * The matching part(before =>) can end with a wildcard: https://example.com/org/* => http://proxy.com/,
 * in this case only specified prefix is replaced, so url https://example.com/org/test.git will be mapped to http://proxy.com/test.git
 * <br>
 * Each rule should be added separately in properties file, the property name should have a prefix {@value jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot#FETCH_URL_MAPPING_PROPERTY_NAME_PREFIX}
 */
public class GitURLMapper {

  private static List<GitUrlReplacementRule> getRules(@NotNull String rulePropertyPrefix) {
    Collection<String> rawRules = TeamCityProperties.getPropertiesWithPrefix(rulePropertyPrefix).values();
    return rawRules.stream()
                   .map(rawRule -> GitUrlReplacementRule.create(rawRule))
                   .filter(rule -> rule != null)
                   .collect(Collectors.toList());
  }
  
  @Nullable
  public static String getModifiedURL(@NotNull String url, @NotNull String rulePropertyPrefix) {
    for (GitUrlReplacementRule rule : getRules(rulePropertyPrefix)) {
      String res = rule.getModifiedURL(url);
      if (res != null) {
        return res;
      }
    }
    
    return null;
  }

  private static class GitUrlReplacementRule {
    private final boolean myHasWildcard;
    @NotNull
    private final String myMatchingString;
    @NotNull
    private final String myReplacementString;
    private GitUrlReplacementRule(@NotNull String matchingString, @NotNull String replacementString, boolean hasWildcard) {
      myMatchingString = matchingString;
      myReplacementString = replacementString;
      myHasWildcard = hasWildcard;
    }

    @Nullable
    public static GitUrlReplacementRule create(@NotNull String fullRule) {
      List<String> splittedRule = Arrays.stream(fullRule.split("=>")).map(s -> s.trim()).collect(Collectors.toList());
      if (splittedRule.size() != 2) {
        return null;
      }
      String rule = splittedRule.get(0);
      String replacementString = splittedRule.get(1);
      String matchingString;
      boolean hasWildcard;

      if (rule.endsWith("*")) {
        matchingString = rule.substring(0, rule.length() - 1);
        hasWildcard = true;
      } else {
        matchingString = rule;
        hasWildcard = false;
      }

      return new GitUrlReplacementRule(matchingString, replacementString, hasWildcard);
    }

    @Nullable
    public String getModifiedURL(@NotNull String originalUrl) {
      if (myHasWildcard) {
        if (originalUrl.startsWith(myMatchingString)) {
          return myReplacementString + originalUrl.substring(myMatchingString.length());
        }
      } else {
        if (originalUrl.equals(myMatchingString)) {
          return myReplacementString;
        }
      }
      return null;
    }
  }
}
