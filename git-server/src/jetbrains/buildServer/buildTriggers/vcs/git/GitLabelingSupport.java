/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.LabelingSupport;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushConnection;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
* @author dmitry.neverov
*/
class GitLabelingSupport implements LabelingSupport {

  private final static Logger LOG = Logger.getInstance(GitLabelingSupport.class.getName());

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final RepositoryManager myRepositoryManager;
  private final TransportFactory myTransportFactory;

  public GitLabelingSupport(@NotNull GitVcsSupport vcs,
                            @NotNull CommitLoader commitLoader,
                            @NotNull RepositoryManager repositoryManager,
                            @NotNull TransportFactory transportFactory) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myRepositoryManager = repositoryManager;
    myTransportFactory = transportFactory;
  }

  public String label(@NotNull String label,
                      @NotNull String version,
                      @NotNull VcsRoot root,
                      @NotNull CheckoutRules checkoutRules) throws VcsException {
    OperationContext context = myVcs.createContext(root, "labelling");
    GitVcsRoot gitRoot = context.getGitRoot();
    try {
      long start = System.currentTimeMillis();
      Repository r = context.getRepository();
      String commitSHA = GitUtils.versionRevision(version);
      RevCommit commit = myCommitLoader.loadCommit(context, gitRoot, commitSHA);
      Git git = new Git(r);
      Ref tagRef = git.tag().setTagger(gitRoot.getTagger(r))
        .setName(label)
        .setObjectId(commit)
        .call();
      if (tagRef.getObjectId() == null || resolve(r, tagRef) == null) {
        LOG.warn("Tag's " + tagRef.getName() + " objectId " + (tagRef.getObjectId() != null ? tagRef.getObjectId().name() + " " : "") + "cannot be resolved");
      } else if (LOG.isDebugEnabled()) {
        LOG.debug("Tag created  " + label + "=" + version + " for " + gitRoot.debugInfo() +
                  " in " + (System.currentTimeMillis() - start) + "ms");
      }
      long pushStart = System.currentTimeMillis();
      synchronized (myRepositoryManager.getWriteLock(gitRoot.getRepositoryDir())) {
        final Transport tn = myTransportFactory.createTransport(r, gitRoot.getRepositoryPushURL(), gitRoot.getAuthSettings());
        try {
          final PushConnection c = tn.openPush();
          try {
            RemoteRefUpdate ru = new RemoteRefUpdate(r, tagRef.getName(), tagRef.getObjectId(), tagRef.getName(), false, null, null);
            c.push(NullProgressMonitor.INSTANCE, Collections.singletonMap(tagRef.getName(), ru));
            LOG.info("Tag  " + label + "=" + version + " was pushed with status " + ru.getStatus() + " for " + gitRoot.debugInfo() +
                     " in " + (System.currentTimeMillis() - pushStart) + "ms");
            switch (ru.getStatus()) {
              case UP_TO_DATE:
              case OK:
                break;
              default:
                String msg = ru.getMessage();
                throw new VcsException("The remote '" + label+ "' tag was not created" +
                                       ", status: " + ru.getStatus() +
                                       (!isEmpty(msg) ? ", message: " + msg : ""));
            }
          } finally {
            c.close();
          }
          return label;
        } finally {
          tn.close();
        }
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }

  @Nullable
  private ObjectId resolve(final Repository r, final Ref tagRef) {
    try {
      return r.resolve(tagRef.getName());
    } catch (IOException e) {
      return null;
    }
  }
}
