/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitUpdateProcess;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * git show-ref
 */
public class ShowRefCommand extends RepositoryCommand {

  private String myRef;

  public ShowRefCommand(AgentSettings settings) {
    super(settings);
  }

  public void setRef(String ref) {
    myRef = ref;
  }


  public List<Ref> execute() {
    GeneralCommandLine cmd = createCommandLine();
    cmd.addParameter("show-ref");
    if (myRef != null)
      cmd.addParameters(myRef);
    try {
      ExecResult result = runCommand(cmd);
      return parse(result.getStdout());
    } catch (VcsException e) {
      return Collections.emptyList();
    }
  }


  private List<Ref> parse(String str) {
    List<Ref> result = new ArrayList<Ref>();
    for (String line : str.split("\n")) {
      String commit = line.substring(0, 40);
      String ref = line.substring(41, line.length());
      result.add(new RefImpl(ref, commit));
    }
    return result;
  }

  private static class RefImpl implements Ref {

    private final String myName;
    private final ObjectId myObjectId;

    RefImpl(String name, String commit) {
      myName = name;
      myObjectId = ObjectId.fromString(commit);
    }

    public String getName() {
      return myName;
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

    public ObjectId getObjectId() {
      return myObjectId;
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
}
