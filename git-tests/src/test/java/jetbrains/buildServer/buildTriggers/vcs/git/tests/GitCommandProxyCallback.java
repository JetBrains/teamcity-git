

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;

public interface GitCommandProxyCallback {
  /**
   * Callback which will be called before or instead of origin method
   *
   * @param method origin method name
   * @param args   origin args
   * @return return null if you what to call origin method next; return Optional with value which will be used as original method return
   */
  @Nullable
  Optional<Object> call(Method method, Object[] args) throws VcsException;
}