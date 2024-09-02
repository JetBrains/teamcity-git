

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created 28.04.2014 20:26
 *
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 */
public class GitFetchService implements FetchService, GitServerExtension {
  @NotNull private final GitVcsSupport myVcs;
  private final ConcurrentHashMap<File, RepositoryStateData> myRepositoryStateDataCache = new ConcurrentHashMap<File, RepositoryStateData>();

  public GitFetchService(@NotNull final GitVcsSupport support) {
    myVcs = support;
    support.addExtension(this);
  }

  public void fetchRepository(@NotNull final VcsRoot root,
                              @NotNull final CheckoutRules rules,
                              @NotNull final FetchRepositoryCallback callback) throws VcsException {
    final OperationContext ctx = myVcs.createContext(root, "Fetch", new FetchCallbackProgress(callback));
    GitVcsRoot gitRoot = ctx.getGitRoot();
    myVcs.getRepositoryManager().runWithDisabledRemove(gitRoot.getRepositoryDir(), () -> {
      try {
        fetchRepositoryImpl(ctx);
      } catch (Exception e) {
        throw ctx.wrapException(e);
      } finally {
        ctx.close();
      }
    });

  }

  @NotNull
  private RepositoryStateData fetchRepositoryImpl(@NotNull final OperationContext ctx) throws VcsException {
    try {
      final RepositoryStateData currentState = myVcs.getCollectChangesPolicy().fetchAllRefs(ctx, ctx.makeRootWithTags());
      myRepositoryStateDataCache.put(key(ctx), currentState);
      return currentState;
    } catch (Exception e) {
      throw ctx.wrapException(e);
    }
  }

  @NotNull
  public RepositoryStateData getOrCreateRepositoryState(@NotNull final OperationContext ctx) throws VcsException {
    final RepositoryStateData cache = myRepositoryStateDataCache.get(key(ctx));
    if (cache != null) return cache;

    return fetchRepositoryImpl(ctx);
  }

  @NotNull
  private File key(@NotNull final OperationContext ctx) throws VcsException {
    return ctx.getGitRoot().getRepositoryDir();
  }
}