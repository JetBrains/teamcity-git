package jetbrains.buildServer.buildTriggers.vcs.git.health;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Walks a Git VCS root's fetch/push URLs, resolving parameter references per build-type instance
 * when the root's URLs contain them, so a Health report can inspect the resolved URL values.
 */
class GitVcsRootUrlWalker {

  private GitVcsRootUrlWalker() {}

  interface ResolvedGitUrlConsumer {
    void accept(@NotNull SVcsRoot root, @Nullable SBuildType buildType, @NotNull String urlType, @NotNull String url);
  }

  static void walk(@NotNull SVcsRoot root, @NotNull HealthStatusScope scope, @NotNull ResolvedGitUrlConsumer consumer) {
    if (!containsParameterReferences(root)) {
      reportForSimpleVcsRoot(root, consumer);
    } else {
      reportForRootWithReferences(root, scope, consumer);
    }
  }

  private static void reportForSimpleVcsRoot(@NotNull SVcsRoot root, @NotNull ResolvedGitUrlConsumer consumer) {
    reportIfPresent(root, null, Constants.FETCH_URL, getFetchUrl(root), consumer);
    reportIfPresent(root, null, Constants.PUSH_URL, getPushUrl(root), consumer);
  }

  private static void reportForRootWithReferences(@NotNull SVcsRoot root, @NotNull HealthStatusScope scope, @NotNull ResolvedGitUrlConsumer consumer) {
    for (SBuildType buildType : scope.getBuildTypes()) {
      if (buildType.containsVcsRoot(root.getId())) {
        final VcsRootInstance vcsRootInstance = buildType.getVcsRootInstanceForParent(root);
        if (vcsRootInstance != null) {
          reportForVcsRootInstance(vcsRootInstance, buildType, consumer);
        }
      }
    }
  }

  private static void reportForVcsRootInstance(@NotNull VcsRootInstance vcsRootInstance, @NotNull SBuildType buildType, @NotNull ResolvedGitUrlConsumer consumer) {
    reportIfPresent(vcsRootInstance.getParent(), buildType, Constants.FETCH_URL, getFetchUrl(vcsRootInstance), consumer);
    reportIfPresent(vcsRootInstance.getParent(), buildType, Constants.PUSH_URL, getPushUrl(vcsRootInstance), consumer);
  }

  private static void reportIfPresent(@NotNull SVcsRoot root, @Nullable SBuildType buildType, @NotNull String urlType, @Nullable String url, @NotNull ResolvedGitUrlConsumer consumer) {
    if (StringUtil.isEmpty(url)) return;
    consumer.accept(root, buildType, urlType, url);
  }

  private static boolean containsParameterReferences(@NotNull SVcsRoot root) {
    final String fetchUrl = StringUtil.notNullize(getFetchUrl(root));
    final String pushUrl = StringUtil.notNullize(getPushUrl(root));
    return ReferencesResolverUtil.mayContainReference(fetchUrl) ||
           ReferencesResolverUtil.mayContainReference(pushUrl);
  }

  @Nullable
  private static String getFetchUrl(@NotNull VcsRoot root) {
    return root.getProperty(Constants.FETCH_URL);
  }

  @Nullable
  private static String getPushUrl(@NotNull VcsRoot root) {
    return root.getProperty(Constants.PUSH_URL);
  }
}
