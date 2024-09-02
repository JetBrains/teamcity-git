

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.vcs.VcsFileModification;
import jetbrains.buildServer.vcs.VcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

public class MockVcsModification implements VcsModification {
  private String myVersion;

  public MockVcsModification(@NotNull String version) {
    myVersion = version;
  }

  @NotNull
  public String getVersion() {
    return myVersion;
  }

  public long getId() {
    throw new UnsupportedOperationException();
  }

  public boolean isPersonal() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public List<VcsFileModification> getChanges() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public VcsFileModification findChangeByPath(final String fileName) {
    throw new UnsupportedOperationException();
  }

  public int getChangeCount() {
    throw new UnsupportedOperationException();
  }

  public String getDisplayVersion() {
    throw new UnsupportedOperationException();
  }

  public String getVersionControlName() {
    throw new UnsupportedOperationException();
  }

  public int compareTo(final VcsModification o) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public String getUserName() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public String getDescription() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public Date getVcsDate() {
    throw new UnsupportedOperationException();
  }
}