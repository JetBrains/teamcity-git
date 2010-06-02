/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git;

/**
 * Submodule checkout policy
 */
public enum SubmodulesCheckoutPolicy {
  /**
   * Ignore submodules
   */
  IGNORE,
  /**
   * Checkout submodules
   */
  CHECKOUT,
  /**
   * Checkout submodules non-recursively
   */
  NON_RECURSIVE_CHECKOUT;

  /**
   * Get policy for sub-submodules if currently specified policy is used
   * @param policy submodule checkout policy of interest
   * @return policy for sub-submodules
   */
  public static SubmodulesCheckoutPolicy getSubSubModulePolicyFor(SubmodulesCheckoutPolicy policy) {
    switch (policy) {
      case IGNORE:
        return IGNORE;
      case CHECKOUT:
        return CHECKOUT;
      case NON_RECURSIVE_CHECKOUT:
        return IGNORE;
      default:
        throw new UnsupportedOperationException("Unknown submodules checkout policy: " + policy);
    }
  }
}
