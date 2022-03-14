package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CachingNativeGitTestConnectionRunner {
  private final GitVcsSupport myVcsSupport;
  private final Map<Integer, String> myCachedResults = new HashMap<>();
  private int myCacheHits = 0;
  private int myProcessed = 0;

  public CachingNativeGitTestConnectionRunner(GitVcsSupport vcsSupport) {
    myVcsSupport = vcsSupport;
  }

  @NotNull
  private static Integer getKey(@NotNull VcsRoot root) {
    final StringBuilder res = new StringBuilder(root.getProperty(Constants.FETCH_URL, ""))
      .append(root.getProperty(Constants.USERNAME));

    final AuthenticationMethod authMethod = Enum.valueOf(AuthenticationMethod.class, root.getProperty(Constants.AUTH_METHOD, AuthenticationMethod.ANONYMOUS.name()));
    res.append(authMethod.name());
    switch (authMethod) {
      case PASSWORD:
        res.append(root.getProperty(Constants.PASSWORD));
        break;
      case ACCESS_TOKEN: {
        res.append(root.getProperty(Constants.TOKEN_ID));
        break;
      }
      case TEAMCITY_SSH_KEY:
        // TODO: uploaded keys with the same name may belong to different projects
        res.append(root.getProperty(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME));
        break;
      case PRIVATE_KEY_FILE:
        res.append(root.getProperty(Constants.PRIVATE_KEY_PATH));
        break;
    }
    return res.toString().hashCode();
  }

  @Nullable
  public String testConnection(@NotNull VcsRoot root) {
    ++myProcessed;

    final Integer key = getKey(root);
    if (myCachedResults.containsKey(key)) {
      ++myCacheHits;
      return myCachedResults.get(key);
    }
    try {
      testConnection(root, false);
    } catch (VcsException e) {
      myCachedResults.put(key, null);
      // if jgit fails, no need to check native git
      return null;
    }

    String error = null;
    try {
      testConnection(root, true);
    } catch (VcsException e) {
      error = e.getMessage();
    }
    myCachedResults.put(key, error);
    return error;
  }

  protected void testConnection(@NotNull VcsRoot root, boolean useNativeGit) throws VcsException {
    IOGuard.allowNetworkAndCommandLine(() -> myVcsSupport.getRemoteRefs(root, useNativeGit));
  }

  public void dispose() {
    myCachedResults.clear();
    myCacheHits = 0;
    myProcessed = 0;
  }

  public int getCacheHits() {
    return myCacheHits;
  }

  public int getCacheSize() {
    return myCachedResults.size();
  }

  public int getProcessed() {
    return myProcessed;
  }
}
