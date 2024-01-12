

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/**
* @author dmitry.neverov
*/
public class RefImpl implements Ref {

  private final String myName;
  private final ObjectId myObjectId;

  public RefImpl(String name, String commit) {
    myName = name;
    myObjectId = ObjectId.fromString(commit);
  }

  public String getName() {
    return myName;
  }

  public ObjectId getObjectId() {
    return myObjectId;
  }

  public boolean isSymbolic() {
    return false;
  }

  public Ref getLeaf() {
    throw new UnsupportedOperationException();
  }

  public Ref getTarget() {
    throw new UnsupportedOperationException();
  }

  public ObjectId getPeeledObjectId() {
    return null;
  }

  public boolean isPeeled() {
    throw new UnsupportedOperationException();
  }

  public Storage getStorage() {
    throw new UnsupportedOperationException();
  }
}