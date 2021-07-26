package jetbrains.buildServer.buildTriggers.vcs.git;

public class BranchStates {
  private final String myOldState;
  private final String myNewState;

  public BranchStates(String oldState, String newState) {
    myOldState = oldState;
    myNewState = newState;
  }

  public String getOldState() {
    return myOldState;
  }

  public String getNewState() {
    return myNewState;
  }

  public boolean isBranchNewlyCreated() {
    return myOldState == null && myNewState != null;
  }

  public boolean isBranchRenewed() {
    if (myNewState == null || myOldState == null) {
      throw new IllegalStateException("at least one of branch states is null");
    }
    return !myNewState.equals(myOldState);
  }
}
