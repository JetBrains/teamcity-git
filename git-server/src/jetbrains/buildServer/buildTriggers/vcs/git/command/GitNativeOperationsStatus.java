package jetbrains.buildServer.buildTriggers.vcs.git.command;

public interface GitNativeOperationsStatus {
  GitNativeOperationsStatus NO_IMPL = new GitNativeOperationsStatus() {
    @Override
    public boolean isNativeGitOperationsEnabled() {
      return false;
    }

    @Override
    public boolean setNativeGitOperationsEnabled(boolean nativeGitOperatoinsEnabled) {
      return false;
    }
  };

  boolean isNativeGitOperationsEnabled();

  public boolean setNativeGitOperationsEnabled(boolean nativeGitOperatoinsEnabled);
}