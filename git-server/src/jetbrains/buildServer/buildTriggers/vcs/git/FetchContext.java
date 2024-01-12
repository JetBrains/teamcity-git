

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class FetchContext {
  @NotNull private final OperationContext myContext;
  @NotNull private final Set<String> myRemoteRefs;
  @NotNull private final CommitLoader myCommitLoader;

  @NotNull private final Collection<CommitLoader.RefCommit> myRevisions = new ArrayList<>();

  public FetchContext(@NotNull final OperationContext context, @NotNull GitVcsSupport vcsSupport) throws VcsException {
    myContext = context;
    myCommitLoader = vcsSupport.getCommitLoader();
    myRemoteRefs = vcsSupport.getRemoteRefs(context.getRoot()).keySet().stream().filter(r -> r.startsWith("refs/")).collect(Collectors.toSet());
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
  private Collection<CommitLoader.RefCommit> expandRefs(@NotNull Map<String, String> revisions, boolean tips) {
    return revisions.entrySet().stream().filter(e -> !isEmpty(e.getKey())).map(e -> new CommitLoader.RefCommit() {
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

  public void fetchIfNoCommitsOrFail() throws VcsException, IOException {
    myCommitLoader.loadCommits(myContext, myContext.getGitRoot().getRepositoryFetchURL().get(), myRevisions, myRemoteRefs);
  }
}