

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import jetbrains.buildServer.util.impl.Lazy;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class FetchContext {
  @NotNull private final OperationContext myContext;
  @NotNull private final Lazy<Map<String, Ref>> myRemoteRefsLazy;
  @NotNull private final CommitLoader myCommitLoader;

  @NotNull private final Collection<RefCommit> myRevisions = new ArrayList<>();
  private boolean myFailFastOnMovedTips;

  public FetchContext(@NotNull final OperationContext context, @NotNull GitVcsSupport vcsSupport) throws VcsException {
    myContext = context;
    myCommitLoader = vcsSupport.getCommitLoader();
    myRemoteRefsLazy = Lazy.create(() -> {
      try {
        return vcsSupport.getRemoteRefs(context.getRoot()).entrySet().stream().filter(e -> e.getKey().startsWith("refs/"))
                         .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      } catch (VcsException e) {
        throw new RuntimeException("Failed to fetch remote refs during the fetch operation", e);
      }
    });
  }

  @NotNull
  public FetchContext withRevisions(@NotNull Map<String, String> revisions, boolean tips) {
    myRevisions.addAll(expandRefs(revisions, tips));
    return this;
  }

  @NotNull
  public FetchContext withFromRevisions(@NotNull Map<String, String> revisions) {
    return withRevisions(revisions, false);
  }

  @NotNull
  public FetchContext withToRevisions(@NotNull Map<String, String> revisions) {
    return withRevisions(revisions, true);
  }

  @NotNull
  private Collection<RefCommit> expandRefs(@NotNull Map<String, String> revisions, boolean tips) {
    return revisions.entrySet().stream().filter(e -> !isEmpty(e.getKey())).map(e -> new RefCommit() {
      @NotNull
      @Override
      public String getRef() {
        return GitUtils.expandRef(e.getKey());
      }

      @NotNull
      @Override
      public String getCommit() {
        return e.getValue();
      }

      @Override
      public boolean isRefTip() {
        return tips;
      }
    }).collect(Collectors.toList());
  }

  /**
   * Makes the fetch fail with the recoverable error at once, rather than after fetching all refs, when the refs of the tips missing after the selective fetch
   * point to other commits in the remote repository, e.g. after a force push; only for a caller collecting changes up to a state, which takes a new state after the error
   */
  @NotNull
  public FetchContext failingFastOnMovedTips() {
    myFailFastOnMovedTips = true;
    return this;
  }

  public void fetchIfNoCommitsOrFail() throws VcsException, IOException {
    fetchIfNoCommitsOrFail(TimeUnit.SECONDS.toMillis(myContext.getPluginConfig().repositoryWriteLockTimeout()));
  }

  /**
   * The same as {@link #fetchIfNoCommitsOrFail()}, but waits for the write lock of the local clone not longer than the specified time
   */
  public void fetchIfNoCommitsOrFail(long writeLockTimeoutMillis) throws VcsException, IOException {
    myCommitLoader.loadCommits(myContext, myContext.getGitRoot().getRepositoryFetchURL().get(), myRevisions, myRemoteRefsLazy, writeLockTimeoutMillis, myFailFastOnMovedTips);
  }

  /**
   * Checks the local clone without taking its write lock.
   * @return true if {@link #fetchIfNoCommitsOrFail()} would fetch some of the revisions
   */
  public boolean isFetchNeeded() throws VcsException {
    return myCommitLoader.isFetchNeeded(myContext, myRevisions);
  }
}