package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.XmlResponseUtil;
import jetbrains.buildServer.controllers.admin.projects.EditVcsRootsController;
import jetbrains.buildServer.diagnostic.web.DiagnosticTab;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.TestConnectionSupport;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.ProjectHierarchyBean;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

public class GitDiagnosticsTab extends DiagnosticTab {

  private final GitRepoOperations myOperations;
  private final GitMainConfigProcessor myMainConfigProcessor;
  private final TestConnectionSupport myTestConnectionSupport;
  private final ProjectManager myProjectManager;

  public GitDiagnosticsTab(@NotNull PagePlaces pagePlaces,
                           @NotNull WebControllerManager controllerManager,
                           @NotNull PluginDescriptor pluginDescriptor,
                           @NotNull GitRepoOperations gitOperations,
                           @NotNull GitMainConfigProcessor mainConfigProcessor,
                           @NotNull GitVcsSupport gitVcsSupport,
                           @NotNull ProjectManager projectManager) {
    super(pagePlaces, "gitStatus", "Git");
    myOperations = gitOperations;
    myMainConfigProcessor = mainConfigProcessor;
    myTestConnectionSupport = gitVcsSupport;
    myProjectManager = projectManager;
    setPermission(Permission.MANAGE_SERVER_INSTALLATION);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("gitStatusTab.jsp"));
    register();
    controllerManager.registerController("/admin/diagnostic/nativeGitStatus.html", new BaseFormXmlController() {
      @Override
      protected ModelAndView doGet(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        // TODO: check permissions
        final Map<String, Object> model = new HashMap<>();
        model.put("projectGitRoots", getProjectGitRoots(request));
        return new ModelAndView(pluginDescriptor.getPluginResourcesPath("vcsRootsContainer.jsp"), model);
      }

      @Override
      protected void doPost(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {
        // TODO: check permissions
        final ActionErrors errors = new ActionErrors();
        if (request.getParameter("switch") == null) {
          // test connection
          final String projectExternalId = request.getParameter("testConnectionProject");
          final String vcsRoots = request.getParameter("testConnectionVcsRoots");
          final SProject project = myProjectManager.findProjectByExternalId(projectExternalId);
          if (StringUtil.isEmpty(projectExternalId)) {
            errors.addError("testConnectionProject", "Must not be empty");
          } else if (project == null) {
            errors.addError("testConnectionProject", "No project found");
          } else if (StringUtil.isEmpty(vcsRoots)) {
            errors.addError("testConnectionVcsRoots", "Must not be empty");
          }
          if (errors.hasErrors()) {
            writeErrors(xmlResponse, errors);
            return;
          }
          try {
            //noinspection ConstantConditions
            project.getVcsRootInstances().stream().filter(ri -> vcsRoots.equals(ri.getParent().getExternalId())).forEach(ri -> {
              try {
                IOGuard.allowNetworkAndCommandLine(() -> myTestConnectionSupport.testConnection(ri));
              } catch (Throwable e) {
                throw new TestConnectionException(e);
              }
            });
          } catch (TestConnectionException e) {
            final Throwable cause = e.getCause();
            errors.addError(EditVcsRootsController.FAILED_TEST_CONNECTION_ERR, cause.getMessage());
          }
          if (errors.hasErrors()) {
            writeErrors(xmlResponse, errors);
          } else {
            XmlResponseUtil.writeTestResult(xmlResponse, "");
          }
        } else {
          final boolean enabled = myMainConfigProcessor.setNativeGitOperationsEnabled("enable".equalsIgnoreCase(request.getParameter("switchNativeGit")));
          xmlResponse.addContent(new Element("nativeGitOperationsEnabled").addContent(String.valueOf(enabled)));
        }
      }
    });
  }

  private static boolean isGitRoot(@NotNull SVcsRoot root) {
    return Constants.VCS_NAME.equals(root.getVcsName());
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);
    // TODO: check permissions
    model.put("nativeGitOperationsEnabled", myMainConfigProcessor.isNativeGitOperationsEnabled());
    try {
      final GitExec gitExec = myOperations.detectGit();
      model.put("gitExec", gitExec);
      model.put("isGitExecError", false);
      model.put("nativeGitOperationsSupported", myOperations.isNativeGitOperationsSupported(gitExec));
      model.put("projectsWithGitRoots", getProjectsWithGitRoots());
      model.put("projectGitRoots", getProjectGitRoots(request));
    } catch (VcsException e) {
      model.put("gitExecError", e);
      model.put("isGitExecError", true);
      model.put("nativeGitOperationsSupported", false);
    }
  }

  @NotNull
  private List<SVcsRoot> getProjectGitRoots(@NotNull HttpServletRequest request) {
    final String selectedProjectExternalId = request.getParameter("selectedProject");
    if (StringUtil.isEmpty(selectedProjectExternalId)) return Collections.emptyList();

    final SProject selectedProject = myProjectManager.findProjectByExternalId(selectedProjectExternalId);
    return selectedProject == null ? Collections.emptyList() : selectedProject.getUsedVcsRoots().stream().filter(r -> isGitRoot(r)).collect(Collectors.toList());
  }

  @NotNull
  private List<ProjectHierarchyBean> getProjectsWithGitRoots() {
    final List<SProject> projects =
      myProjectManager.getActiveProjects().stream().filter(p -> p.getOwnVcsRoots().stream().anyMatch(r -> isGitRoot(r))).collect(Collectors.toList());
    return ProjectHierarchyBean.getProjectsFor(projects, true);
  }

  private static final class TestConnectionException extends RuntimeException {
    public TestConnectionException(@NotNull Throwable cause) {
      super(cause);
    }
  }
}
