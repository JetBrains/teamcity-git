/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

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
    throw new UnsupportedOperationException();
  }

  public Ref getLeaf() {
    throw new UnsupportedOperationException();
  }

  public Ref getTarget() {
    throw new UnsupportedOperationException();
  }

  public ObjectId getPeeledObjectId() {
    throw new UnsupportedOperationException();
  }

  public boolean isPeeled() {
    throw new UnsupportedOperationException();
  }

  public Storage getStorage() {
    throw new UnsupportedOperationException();
  }
}
