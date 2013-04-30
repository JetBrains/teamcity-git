package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.BranchSpecProvider;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.vcs.api.data.VcsRepositoryState;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static java.util.Collections.emptySet;
import static jetbrains.buildServer.util.CollectionsUtil.setOf;

public class ShortDefaultBranchNameProvider implements BranchSpecProvider, GitServerExtension {

  @NotNull
  public Set<String> getBranchSpecs(@NotNull BuildTypeEx buildType,
                                    @NotNull VcsRootInstance root,
                                    @NotNull VcsRepositoryState repositoryState) {
    if (!Constants.VCS_NAME.equals(root.getVcsName()))
      return emptySet();
    String ref = getRef(root);
    if (ref.startsWith("refs/"))
      return emptySet(); //full ref is specified, no need for short name
    return setOf("refs/heads/(" + ref + ")");
  }

  public boolean isDefinedFor(@NotNull BuildTypeEx buildTypeEx) {
    return true;
  }

  private String getRef(@NotNull VcsRootInstance root) {
    String ref = root.getProperty(Constants.BRANCH_NAME);
    return StringUtil.isEmptyOrSpaces(ref) ? "master" : ref;
  }
}
