package jetbrains.buildServer.buildTriggers.vcs.git.tests.util;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * This class is created to handle internal properties setting differently from the BaseTestCase.
 * Instead of just removing property after test finishes, it restores the value that were set before test.
 */
public class InternalPropertiesHandler {
  private final Map<String, String> oldValues;

  public InternalPropertiesHandler() {
    oldValues = new HashMap<>();
  }


  synchronized public void setInternalProperty(@NotNull String key, @NotNull String value) {
    if (!oldValues.containsKey(key)) {
      oldValues.put(key, System.getProperty(key));
    }
    System.setProperty(key, value);
  }

  synchronized public void tearDown() {
    for (Map.Entry<String, String> entry : oldValues.entrySet()) {
      if (entry.getValue() != null) {
        System.setProperty(entry.getKey(), entry.getValue());
      } else {
        System.clearProperty(entry.getKey());
      }
    }
    oldValues.clear();
  }
}
