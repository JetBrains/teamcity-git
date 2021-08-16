package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.SpaceExternalChangeViewerExtension;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.changeViewers.PropertyType;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthProvider;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootInstanceImpl;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static jetbrains.buildServer.serverSide.oauth.space.SpaceOAuthKeys.*;
import static org.junit.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class SpaceExternalChangeViewerExtensionTest {

  private ExtensionHolder myExtensionHolder;
  private OAuthConnectionsManager myOAuthConnectionsManager;

  private final static String hostedOnJetBrainsSide = "https://git.jetbrains.space/golubinov/oauthspace/test-epo.git";
  private final static String hostedOnJetBrainsSideServerUrl = "https://golubinov.jetbrains.space/";

  private final static String selfHosted = "https://git.jetbrains.team/tc/TeamCity.git";
  private final static String selfHostedServerUrl = "https://jetbrains.team";

  @BeforeMethod
  public void setUp() {
    myExtensionHolder = Mockito.mock(ExtensionHolder.class);
    myOAuthConnectionsManager = Mockito.mock(OAuthConnectionsManager.class);
  }

  @Test
  void jetbrainsSpaceVcsRoot() {
    // given
    final VcsRootInstanceImpl vcsRoot = Mockito.mock(VcsRootInstanceImpl.class);
    Mockito.doReturn(Constants.VCS_NAME).when(vcsRoot).getVcsName();
    Mockito.doReturn(hostedOnJetBrainsSide).when(vcsRoot).getProperty(Constants.FETCH_URL);

    final OAuthConnectionDescriptor connection = Mockito.mock(OAuthConnectionDescriptor.class);
    Mockito.doReturn(ImmutableMap.of(
      SPACE_SERVER_URL, hostedOnJetBrainsSideServerUrl,
      SPACE_CLIENT_ID, "ignore",
      SPACE_CLIENT_SECRET, "ignore"
    )).when(connection).getParameters();

    // test
    SpaceExternalChangeViewerExtension extension = mySpaceExternalChangeViewerExtension();

    final Map<String, String> availableProperties = extension.getAvailableProperties(vcsRoot);

    assertEquals(
      availableProperties,
      ImmutableMap.of(
        PropertyType.CHANGE_SET_TYPE, "https://golubinov.jetbrains.space/p/oauthspace/repositories/test-epo/revision/${changeSetId}",
        PropertyType.LINK_TEXT, "Open in Space",
        PropertyType.LINK_ICON_CLASS, "tc-icon_space"
      )
    );
  }

  @Test
  void jetbrainsTeamVcsRoot() {
    // given
    final VcsRootInstanceImpl vcsRoot = Mockito.mock(VcsRootInstanceImpl.class);
    Mockito.doReturn(Constants.VCS_NAME).when(vcsRoot).getVcsName();
    Mockito.doReturn(selfHosted).when(vcsRoot).getProperty(Constants.FETCH_URL);

    final SProject project = Mockito.mock(SProject.class);

    final SVcsRoot sVcsRoot = Mockito.mock(SVcsRoot.class);
    Mockito.doReturn(sVcsRoot).when(vcsRoot).getParent();
    Mockito.doReturn(project).when(sVcsRoot).getProject();

    final OAuthConnectionDescriptor connection = Mockito.mock(OAuthConnectionDescriptor.class);
    Mockito.doReturn(ImmutableList.of(connection)).when(myOAuthConnectionsManager).getAvailableConnectionsOfType(project, SpaceOAuthProvider.TYPE);

    Mockito.doReturn(ImmutableMap.of(
      SPACE_SERVER_URL, selfHostedServerUrl,
      SPACE_CLIENT_ID, "ignore",
      SPACE_CLIENT_SECRET, "ignore"
    )).when(connection).getParameters();

    // test
    SpaceExternalChangeViewerExtension extension = mySpaceExternalChangeViewerExtension();

    final Map<String, String> availableProperties = extension.getAvailableProperties(vcsRoot);

    assertEquals(
      availableProperties,
      ImmutableMap.of(
        PropertyType.CHANGE_SET_TYPE, "https://jetbrains.team/p/tc/repositories/TeamCity/revision/${changeSetId}",
        PropertyType.LINK_TEXT, "Open in Space",
        PropertyType.LINK_ICON_CLASS, "tc-icon_space"
      )
    );
  }

  @Test
  void jetbrainsTeamVcsRoot_connectionNotFound() {
    // given
    final VcsRootInstanceImpl vcsRoot = Mockito.mock(VcsRootInstanceImpl.class);
    Mockito.doReturn(Constants.VCS_NAME).when(vcsRoot).getVcsName();
    Mockito.doReturn(selfHosted).when(vcsRoot).getProperty(Constants.FETCH_URL);

    final SProject project = Mockito.mock(SProject.class);

    final SVcsRoot sVcsRoot = Mockito.mock(SVcsRoot.class);
    Mockito.doReturn(sVcsRoot).when(vcsRoot).getParent();
    Mockito.doReturn(project).when(sVcsRoot).getProject();

    // test
    SpaceExternalChangeViewerExtension extension = mySpaceExternalChangeViewerExtension();

    final Map<String, String> availableProperties = extension.getAvailableProperties(vcsRoot);

    assertNull(availableProperties);
  }

  @Test
  void jetbrainsTeamVcsRoot_urlNotFound() {
    // given
    final VcsRootInstanceImpl vcsRoot = Mockito.mock(VcsRootInstanceImpl.class);
    Mockito.doReturn(Constants.VCS_NAME).when(vcsRoot).getVcsName();

    // test
    SpaceExternalChangeViewerExtension extension = mySpaceExternalChangeViewerExtension();

    final Map<String, String> availableProperties = extension.getAvailableProperties(vcsRoot);

    assertNull(availableProperties);
  }

  @Test
  void jetbrainsTeamVcsRoot_urlIsIncorrect() {
    // given
    final VcsRootInstanceImpl vcsRoot = Mockito.mock(VcsRootInstanceImpl.class);
    Mockito.doReturn(Constants.VCS_NAME).when(vcsRoot).getVcsName();
    Mockito.doReturn("").when(vcsRoot).getProperty(Constants.FETCH_URL);

    // test
    SpaceExternalChangeViewerExtension extension = mySpaceExternalChangeViewerExtension();

    final Map<String, String> availableProperties = extension.getAvailableProperties(vcsRoot);

    assertNull(availableProperties);
  }

  @Test
  void jetbrainsTeamVcsRoot_vcsNameIsIncorrect() {
    // given
    final VcsRootInstanceImpl vcsRoot = Mockito.mock(VcsRootInstanceImpl.class);
    Mockito.doReturn("VCS").when(vcsRoot).getVcsName();

    // test
    SpaceExternalChangeViewerExtension extension = mySpaceExternalChangeViewerExtension();

    final Map<String, String> availableProperties = extension.getAvailableProperties(vcsRoot);

    assertNull(availableProperties);
  }

  @Test
  void jetbrainsTeamVcsRoot_twoConnections() {
    // given
    final VcsRootInstanceImpl vcsRoot = Mockito.mock(VcsRootInstanceImpl.class);
    Mockito.doReturn(Constants.VCS_NAME).when(vcsRoot).getVcsName();
    Mockito.doReturn(selfHosted).when(vcsRoot).getProperty(Constants.FETCH_URL);

    final SProject project = Mockito.mock(SProject.class);

    final SVcsRoot sVcsRoot = Mockito.mock(SVcsRoot.class);
    Mockito.doReturn(sVcsRoot).when(vcsRoot).getParent();
    Mockito.doReturn(project).when(sVcsRoot).getProject();

    final OAuthConnectionDescriptor connection1 = Mockito.mock(OAuthConnectionDescriptor.class);
    final OAuthConnectionDescriptor connection2 = Mockito.mock(OAuthConnectionDescriptor.class);
    Mockito.doReturn(ImmutableList.of(connection1, connection2)).when(myOAuthConnectionsManager).getAvailableConnectionsOfType(project, SpaceOAuthProvider.TYPE);

    Mockito.doReturn(ImmutableMap.of(
      SPACE_SERVER_URL, hostedOnJetBrainsSideServerUrl,
      SPACE_CLIENT_ID, "ignore",
      SPACE_CLIENT_SECRET, "ignore"
    )).when(connection1).getParameters();

    Mockito.doReturn(ImmutableMap.of(
      SPACE_SERVER_URL, selfHostedServerUrl,
      SPACE_CLIENT_ID, "ignore",
      SPACE_CLIENT_SECRET, "ignore"
    )).when(connection2).getParameters();

    // test
    SpaceExternalChangeViewerExtension extension = mySpaceExternalChangeViewerExtension();

    final Map<String, String> availableProperties = extension.getAvailableProperties(vcsRoot);

    assertEquals(
      availableProperties,
      ImmutableMap.of(
        PropertyType.CHANGE_SET_TYPE, "https://jetbrains.team/p/tc/repositories/TeamCity/revision/${changeSetId}",
        PropertyType.LINK_TEXT, "Open in Space",
        PropertyType.LINK_ICON_CLASS, "tc-icon_space"
      )
    );
  }

  @NotNull
  private SpaceExternalChangeViewerExtension mySpaceExternalChangeViewerExtension() {
    return new SpaceExternalChangeViewerExtension(myExtensionHolder, myOAuthConnectionsManager);
  }
}
