package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class PropertiesHelper {
  /**
   * if prefix = "teamcity.git.https.credentials"
   * properties map {"teamcity.git.https.credentials.alias.v1" -> "123"} will be converted to {"alias" -> {"teamcity.git.htts.credentials.v1" -> "123"}}
   * and so on
   */
  public static Map<String, Map<String, String>> aggregatePropertiesByAlias(@NotNull Map<String, String> properties, @NotNull String prefix) {
    if (StringUtil.isEmptyOrSpaces(prefix))
      new HashMap<>();

    Map<String, Map<String, String>> result = new HashMap<>();
    if (prefix.endsWith("."))
      prefix = prefix.substring(0, prefix.length() - 1);

    for (Map.Entry<String, String> entry : properties.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith(prefix)) {
        if (key.equals(prefix)) {
          associatePropertyWithAlias(result, "", key, entry.getValue());
        } else {
          String rest = key.substring(prefix.length()+1); //remoeve prefix.
          String[] aliasAndPostfix = rest.split("\\.", 2);

          if (aliasAndPostfix.length == 1 && !aliasAndPostfix[0].isEmpty()) {
            associatePropertyWithAlias(result, "", key, entry.getValue());
          } else if (aliasAndPostfix.length == 2 && !aliasAndPostfix[1].isEmpty()) {
            associatePropertyWithAlias(result, aliasAndPostfix[0], prefix + "." + aliasAndPostfix[1], entry.getValue());
          }
        }
      }
    }
    return result;
  }

  private static void associatePropertyWithAlias(@NotNull Map<String, Map<String, String>> aggregatedProperties,
                                                 @NotNull String alias,
                                                 @NotNull String key,
                                                 @NotNull String value) {
    aggregatedProperties.computeIfAbsent(alias, a -> new HashMap<String, String>());
    aggregatedProperties.get(alias).put(key, value);
  }
}
