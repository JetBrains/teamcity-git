package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class GitCommandCredentials {
  private final List<ExtraHTTPCredentials> myCredentials = new ArrayList<>();

  private boolean myStoresOnlyDefaultCredential = false;

  @NotNull
  public List<ExtraHTTPCredentials> getCredentials() {
    return new ArrayList<>(myCredentials);
  }

  public void addAll(@NotNull Collection<ExtraHTTPCredentials> credentials) {
    if (!myStoresOnlyDefaultCredential) {
      myCredentials.addAll(credentials);
    }
  }

  public boolean isStoresOnlyDefaultCredential() {
    return myStoresOnlyDefaultCredential;
  }

  public void add(@NotNull ExtraHTTPCredentials credential) {
    if (!myStoresOnlyDefaultCredential) {
      myCredentials.add(credential);
    }
  }

  public void setStoresOnlyDefaultCredential() {
    if (myCredentials.size() == 1)
      myStoresOnlyDefaultCredential = true;
  }
}
