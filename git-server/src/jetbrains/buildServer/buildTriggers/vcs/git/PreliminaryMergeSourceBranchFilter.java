package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.BranchFilter;
import java.util.function.*;
import jetbrains.buildServer.vcs.spec.BranchSpecs;
import org.jetbrains.annotations.NotNull;

public class PreliminaryMergeSourceBranchFilter implements Predicate<String> {
  private final BranchFilter myBranchFilter;

  PreliminaryMergeSourceBranchFilter(@NotNull BranchSpecs branchSpecs, @NotNull String branchFilterPatterns) {
    myBranchFilter = branchSpecs.createFilter(branchFilterPatterns);
  }

  @Override
  public boolean test(String branchName) {
    return myBranchFilter.accept(new Branch() {
      @NotNull
      @Override
      public String getName() {
        return branchName;
      }

      @NotNull
      @Override
      public String getDisplayName() {
        return branchName;
      }

      @Override
      public boolean isDefaultBranch() {
        return false;
      }
    });
  }
}
