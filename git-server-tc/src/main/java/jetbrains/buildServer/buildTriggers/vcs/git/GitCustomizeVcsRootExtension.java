package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.controllers.admin.projects.setupFromUrl.SetupObjectFromResourceBean;
import jetbrains.buildServer.controllers.admin.projects.setupFromUrl.SetupObjectFromResourcePageExtension;
import jetbrains.buildServer.serverSide.discovery.VcsResourceDiscoveryExtension;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.SimplePageExtension;
import org.jetbrains.annotations.NotNull;

/**
 * Extension for customizing git VCS root parameters when creating from repository URL.
 * Currently it's possible to customize default branch and branch spec.
 *
 * @since 2021.1
 */
public class GitCustomizeVcsRootExtension extends SimplePageExtension implements GitServerExtension {

  public GitCustomizeVcsRootExtension(@NotNull PagePlaces pagePlaces, @NotNull PluginDescriptor pluginDescriptor) {
    super(pagePlaces, PlaceId.ADMIN_CUSTOMIZE_VCS_ROOT, GitServerExtension.class.getName(), pluginDescriptor.getPluginResourcesPath("gitCustomizeBranches.jsp"));
    register();
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    if (!super.isAvailable(request)) return false;

    final SetupObjectFromResourceBean projectSetupBean = getSetupObjectFromResourceBean(request);
    return projectSetupBean != null && Constants.VCS_NAME.equals(projectSetupBean.getDiscoveredResource().getParameters().get(VcsResourceDiscoveryExtension.VCS_NAME_PROP_KEY));
  }

  protected SetupObjectFromResourceBean getSetupObjectFromResourceBean(final HttpServletRequest request) {
    return (SetupObjectFromResourceBean)request.getAttribute(SetupObjectFromResourcePageExtension.SETUP_OBJECT_FROM_RESOURCE_BEAN_KEY);
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);

    final SetupObjectFromResourceBean projectSetupBean = getSetupObjectFromResourceBean(request);
    if (projectSetupBean != null) {
      projectSetupBean.getDiscoveredResource().getParameters().putIfAbsent(Constants.BRANCH_SPEC, "refs/heads/*");
    }
  }
}
