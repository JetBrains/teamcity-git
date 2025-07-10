

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.TeamCityProperties;

/**
 * Submodule checkout policy
 */
public enum SubmodulesCheckoutPolicy {
  /**
   * Ignore submodules
   */
  IGNORE(true),
  /**
   * Checkout submodules
   */
  CHECKOUT(false),
  CHECKOUT_IGNORING_ERRORS(true),
  /**
   * Checkout submodules non-recursively
   */
  NON_RECURSIVE_CHECKOUT(false),
  NON_RECURSIVE_CHECKOUT_IGNORING_ERRORS(true);

  private final boolean myIgnoreSubmodulesErrors;

  SubmodulesCheckoutPolicy() {
    this(true);
  }

  SubmodulesCheckoutPolicy(boolean ignoreSubmodulesErrors) {
    myIgnoreSubmodulesErrors = ignoreSubmodulesErrors;
  }

  public boolean isIgnoreSubmodulesErrors() {
    return myIgnoreSubmodulesErrors || TeamCityProperties.getBooleanOrTrue(Constants.IGNORE_SUBMODULE_ERRORS);
  }

  public static SubmodulesCheckoutPolicy getPolicyWithErrorsIgnored(SubmodulesCheckoutPolicy originalPolicy, boolean ignoreSubmodulesErrors) {
    if (ignoreSubmodulesErrors) {
      switch (originalPolicy) {
        case IGNORE:
          return IGNORE;
        case CHECKOUT:
        case CHECKOUT_IGNORING_ERRORS:
          return CHECKOUT_IGNORING_ERRORS;
        case NON_RECURSIVE_CHECKOUT:
        case NON_RECURSIVE_CHECKOUT_IGNORING_ERRORS:
          return NON_RECURSIVE_CHECKOUT_IGNORING_ERRORS;
        default:
          throw new UnsupportedOperationException("Unknown submodules checkout policy: " + originalPolicy);
      }
    } else {
      switch (originalPolicy) {
        case IGNORE:
          return IGNORE;
        case CHECKOUT:
        case CHECKOUT_IGNORING_ERRORS:
          return CHECKOUT;
        case NON_RECURSIVE_CHECKOUT:
        case NON_RECURSIVE_CHECKOUT_IGNORING_ERRORS:
          return NON_RECURSIVE_CHECKOUT;
        default:
          throw new UnsupportedOperationException("Unknown submodules checkout policy: " + originalPolicy);
      }
    }
  }

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
      case CHECKOUT_IGNORING_ERRORS:
        return CHECKOUT_IGNORING_ERRORS;
      case NON_RECURSIVE_CHECKOUT:
      case NON_RECURSIVE_CHECKOUT_IGNORING_ERRORS:
        return IGNORE;
      default:
        throw new UnsupportedOperationException("Unknown submodules checkout policy: " + policy);
    }
  }
}