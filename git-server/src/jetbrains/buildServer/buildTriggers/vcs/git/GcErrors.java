

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class GcErrors {

  private final ConcurrentMap<File, String> myErrors = new ConcurrentHashMap<>();

  void registerError(@NotNull File cloneDir, @NotNull String error) {
    myErrors.put(cloneDir, error);
  }

  void registerError(@NotNull File cloneDir, @NotNull Exception error) {
    myErrors.put(cloneDir, error.toString());
  }

  void registerError(@NotNull File cloneDir, @NotNull String description, @NotNull Exception error) {
    myErrors.put(cloneDir, description + " " + error.toString());
  }

  void clearError(@NotNull File cloneDir) {
    myErrors.remove(cloneDir);
  }

  public void retainErrors(@NotNull Collection<File> files) {
    myErrors.keySet().retainAll(new HashSet<>(files));
  }

  @NotNull
  public Map<File, String> getErrors() {
    return new HashMap<>(myErrors);
  }
}