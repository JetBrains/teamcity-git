package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy;

import com.intellij.openapi.util.Pair;
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.CommitChange;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.CommitList;
import org.jetbrains.annotations.NotNull;

/**
 * A client to communicate with Git Proxy API(Space git API). The methods are the same as in the GitRepoApi from the Space repository
 */
public interface GitRepoApi {
  /**
   * @param query Each clause produces commits set, and the result is intersection of all clauses.
   *    <br>
   *    Possible clauses:
   *    <ul>
   *      <li>"id": Value is the list of commit ids (hashes, abbreviated hashes, or refs)</li>
   *      <li>"id-range": Value is the list of commit id ranges (hash to include subtree. ^hash to exclude subtree. hash1..hash2 equivalent to ^hash1 hash2)</li>
   *      <li>"around-id": Few commits aronf the given</li>
   *      <li>"head": reachable from the given commit</li>
   *      <li>"all-refs": Include all heads (not only refs/heads) as starting set</li>
   *      <li>"branch": HEAD..branch</li>
   *      <li>"unique": include only unique commits from given branch (i.e. not available from any ohter)</li>
   *      <li>"author": Include commits by given author email</li>
   *      <li>"date": from..to. Commit timestamps are in seconds (AKA git time)</li>
   *      <li>"text": match either commit message or hash</li>
   *      <li>"path": Match commits chich have path in change</li>
   *      <li>"no-merges": true to exclude merge commits</li>
   *      <li>"ancestor": Include only commits which have given as ancestor (i.e. all children of ancestor)</li>
   *      <li>"first-parent": true to walk only first parents (i.e. exclude merged branches)</li>
   *    </ul>
   * @param skip paging parameter, the number of commits to skip from the start of the result
   * @param limit paging parameter, limits the number of commits in response
   * @param layout true if should return information about graph layout
   * @param commitsInfo true if should return commit information
   * @return List commits matching query. The resulted commits are returned in topo-sorted order
   */
  CommitList listCommits(@NotNull List<Pair<String, List<String>>> query,
                                int skip,
                                int limit,
                                boolean layout,
                                boolean commitsInfo);

  /**
   * @param commits list of commit hashes
   * @param detectRename true if should return information about renamed files, this is expensive operation
   * @param listDirectories true if should return information not only about changed files, but also about directories
   * @param calcDiffSize true if should return diff size for commits
   * @param inferMergeCommitChanges true if should produce single commit change for merge commits comparing it to parent commits,
   *                                otherwise the changes are returned for each edge
   * @param limit limit of file changes for each commit
   * @return information about which files were added/deleted/modified for each commit
   */ 
  List<CommitChange> listChanges(@NotNull List<String> commits,
                                 boolean detectRename,
                                 boolean listDirectories,
                                 boolean calcDiffSize,
                                 boolean inferMergeCommitChanges,
                                 int limit);
}
