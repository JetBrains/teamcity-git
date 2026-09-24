package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.SGitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelper;
import jetbrains.buildServer.serverSide.oauth.TokenRefresher;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.BDDAssertions.then;

public class DisallowedTransportUrlTest extends BaseTestCase {

  @Test
  public void creation_of_GitVcsRoots_with_disallowed_transport_fetch_url_throws() {
    VcsRoot root = VcsRootBuilder.vcsRoot().withFetchUrl("ext::sh /tmp/x.sh").build();

    try {
      new SGitVcsRoot(Mockito.mock(MirrorManager.class), root, Mockito.mock(URIishHelper.class), Mockito.mock(TokenRefresher.class));
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).doesNotContain("local file").doesNotContain("network protocols like SSH or HTTPS");
      then(e.getMessage()).contains("Allowed:").contains(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS);
    }
  }

  @Test
  public void creation_of_GitVcsRoots_with_disallowed_transport_push_url_throws() {
    VcsRoot root = VcsRootBuilder.vcsRoot().withFetchUrl("https://example.com/owner/repo.git").withPushUrl("ext::sh /tmp/x.sh").build();

    try {
      new SGitVcsRoot(Mockito.mock(MirrorManager.class), root, Mockito.mock(URIishHelper.class), Mockito.mock(TokenRefresher.class));
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).doesNotContain("local file").doesNotContain("network protocols like SSH or HTTPS");
      then(e.getMessage()).contains("Allowed:").contains(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS);
    }
  }
}
