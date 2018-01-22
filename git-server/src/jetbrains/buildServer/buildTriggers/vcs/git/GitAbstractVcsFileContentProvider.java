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

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GitAbstractVcsFileContentProvider implements VcsFileContentProvider {

  private static final Logger LOG = Logger.getInstance(GitAbstractVcsFileContentProvider.class.getName());
  protected final GitVcsSupport myVcs;

  public GitAbstractVcsFileContentProvider(@NotNull GitVcsSupport vcs) {
    myVcs = vcs;
  }

  @NotNull
  public byte[] getContent(@NotNull VcsModification vcsModification,
                           @NotNull VcsChangeInfo change,
                           @NotNull VcsChangeInfo.ContentType contentType,
                           @NotNull VcsRoot vcsRoot) throws VcsException {
    String vcsChangeVersion = contentType == VcsChangeInfo.ContentType.BEFORE_CHANGE
                              ? change.getBeforeChangeRevisionNumber()
                              : change.getAfterChangeRevisionNumber();
    String version = null;
    if (!isValid(vcsChangeVersion)) {
      LOG.warn("Invalid version '" + vcsChangeVersion + "', try using version from VCS modification");
      switch (contentType) {
        case AFTER_CHANGE:
          version = vcsModification.getVersion();
          break;
        case BEFORE_CHANGE:
          version = getParentRevision(vcsRoot, vcsModification);
          break;
        default:
          throw new VcsException("Unknown contentType " + contentType);
      }
    } else {
      version = vcsChangeVersion;
    }

    if (!isValid(version)) {
      //give up if we failed to retrieve version from both change and modification
      LOG.warn("Invalid version " + vcsChangeVersion + ", change: " + change + ", modification version: " + vcsModification.getVersion());
      throw new VcsException("Invalid version '" + vcsChangeVersion + "'");
    }

    String file = change.getRelativeFileName();
    return getContent(file, vcsRoot, version);
  }

  @Nullable
  private String getParentRevision(@NotNull VcsRoot root, @NotNull VcsModification m) throws VcsException {
    String version = m.getVersion();
    if (!isValid(version))
      return null;
    OperationContext context = myVcs.createContext(root, "compute parent revision of " + version);
    try {
      GitVcsRoot gitRoot = context.getGitRoot();
      RevCommit c = myVcs.getCommitLoader().loadCommit(context, gitRoot, version);
      if (c.getParents().length == 0)
        return null;
      return c.getParent(0).name();
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }


  private boolean isValid(@Nullable String version) {
    if (version == null)
      return false;
    try {
      ObjectId.fromString(version);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
