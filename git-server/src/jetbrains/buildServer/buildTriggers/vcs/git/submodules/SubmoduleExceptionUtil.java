

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class SubmoduleExceptionUtil {

  static void addAffectedBranches(@NotNull StringBuilder result, @NotNull Set<String> branches) {
    if (!branches.isEmpty()) {
      List<String> sorted = new ArrayList<String>(branches);
      Collections.sort(sorted);
      result.append("; affected ").append(StringUtil.pluralize("branch", branches.size())).append(": ");
      StringUtil.join(", ", sorted, result);
    }
  }

}