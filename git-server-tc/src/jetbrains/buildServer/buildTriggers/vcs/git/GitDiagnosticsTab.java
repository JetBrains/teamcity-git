package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.AjaxRequestProcessor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.XmlResponseUtil;
import jetbrains.buildServer.diagnostic.web.DiagnosticTab;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.web.CSRFFilter;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.ProjectHierarchyBean;
import jetbrains.buildServer.web.util.SessionUser;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

public class GitDiagnosticsTab extends DiagnosticTab {

  private final GitVcsSupport myVcsSupport;
  private final GitRepoOperations myOperations;
  private final GitMainConfigProcessor myMainConfigProcessor;
  private final ProjectManager myProjectManager;

  public GitDiagnosticsTab(@NotNull PagePlaces pagePlaces,
                           @NotNull WebControllerManager controllerManager,
                           @NotNull PluginDescriptor pluginDescriptor,
                           @NotNull GitVcsSupport vcsSupport,
                           @NotNull GitRepoOperations gitOperations,
                           @NotNull GitMainConfigProcessor mainConfigProcessor,
                           @NotNull ProjectManager projectManager) {
    super(pagePlaces, "gitStatus", "Git");
    myVcsSupport = vcsSupport;
    myOperations = gitOperations;
    myMainConfigProcessor = mainConfigProcessor;
    myProjectManager = projectManager;
    setPermission(Permission.MANAGE_SERVER_INSTALLATION);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("gitStatusTab.jsp"));
    register();
    controllerManager.registerController("/admin/diagnostic/nativeGitStatus.html", new BaseController() {
      @Nullable
      @Override
      protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
        checkPermissions(request);
        if (isGet(request)) {
          final Map<String, Object> model = new HashMap<>();
          model.put("projectGitRoots", getProjectGitRoots(request));
          return new ModelAndView(pluginDescriptor.getPluginResourcesPath("vcsRootsContainer.jsp"), model);
        }
        CSRFFilter.setSessionAttribute(request.getSession(true));
        if (request.getParameter("switch") == null) {
          // validate parameters
          final ActionErrors errors = new ActionErrors();
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
          if (!errors.hasErrors()) {
            // test connection
            final boolean isRootProject = project.isRootProject();
            final Map<SVcsRoot, List<TestConnectionException>> testConnectionErrors = new HashMap<>();
            if ("ALL".equals(vcsRoots)) {
              for (VcsRootInstance ri : project.getVcsRootInstances()) {
                if (!isGitRoot(ri)) continue;
                try {
                  IOGuard.allowNetworkAndCommandLine(() -> myVcsSupport.getRemoteRefs(ri, false));
                } catch (VcsException e) {
                  // jgit fails, no need to check native git
                  continue;
                }
                try {
                  IOGuard.allowNetworkAndCommandLine(() -> myVcsSupport.getRemoteRefs(ri, true));
                } catch (Throwable e) {
                  List<TestConnectionException> rootErrors = testConnectionErrors.get(ri.getParent());
                  if (rootErrors == null) {
                    rootErrors = new ArrayList<>();
                    testConnectionErrors.put(ri.getParent(), rootErrors);
                  }
                  rootErrors.add(new TestConnectionException(e, ri.getUsages().keySet()));
                }
              }
              if (!testConnectionErrors.isEmpty()) {
                final Map<String, Object> model = new HashMap<>();
                model.put("testConnectionErrors", testConnectionErrors);
                model.put("testConnectionProject", project);
                model.put("testConnectionTimestamp", Dates.now().toString());
                return new ModelAndView(pluginDescriptor.getPluginResourcesPath("testConnectionErrors.jsp"), model);
              }
            } else {
              if (myProjectManager.findVcsRootByExternalId(vcsRoots) == null) {
                errors.addError("testConnectionVcsRoots", "No VCS root found");
              } else {
                try {
                  project.getVcsRootInstances().stream().filter(ri -> isGitRoot(ri) && vcsRoots.equals(ri.getParent().getExternalId())).forEach(ri -> {
                    try {
                      IOGuard.allowNetworkAndCommandLine(() -> myVcsSupport.getRemoteRefs(ri, true));
                    } catch (Throwable e) {
                      throw new TestConnectionException(e, ri.getUsages().keySet());
                    }
                  });
                } catch (TestConnectionException e) {
                  final Map<String, Object> model = new HashMap<>();
                  model.put("testConnectionError", e.getCause().getMessage());
                  model.put("affectedBuildTypes", e.getAffectedBuildTypes());
                  model.put("isRootProject", isRootProject);
                  return new ModelAndView(pluginDescriptor.getPluginResourcesPath("testConnectionError.jsp"), model);
                }
              }
            }
          }
          new AjaxRequestProcessor().processRequest(request, response, new AjaxRequestProcessor.RequestHandler() {
            @Override
            public void handleRequest(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {
              if (errors.hasErrors()) {
                errors.serialize(xmlResponse);
              } else {
                XmlResponseUtil.writeTestResult(xmlResponse, "");
              }
            }
          });
        } else {
          new AjaxRequestProcessor().processRequest(request, response, new AjaxRequestProcessor.RequestHandler() {
            @Override
            public void handleRequest(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {
              final boolean enabled = myMainConfigProcessor.setNativeGitOperationsEnabled("enable".equalsIgnoreCase(request.getParameter("switchNativeGit")));
              xmlResponse.addContent(new Element("nativeGitOperationsEnabled").addContent(String.valueOf(enabled)));
            }
          });
        }
        return null;
      }
    });
  }

  private void checkPermissions(@NotNull HttpServletRequest request) {
    final SUser user = SessionUser.getUser(request);
    if (!checkUserPermissions(user)) {
      throw new AccessDeniedException(user, "Not enough permissions");
    }
  }

  private static boolean isGitRoot(@NotNull VcsRoot root) {
    return Constants.VCS_NAME.equals(root.getVcsName());
  }

  public static boolean isEnabled() {
    return TeamCityProperties.getBoolean("teamcity.git.diagnosticsTab.enabled");
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    return super.isAvailable(request) && isEnabled();
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);
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

  public static final class TestConnectionException extends RuntimeException {
    private final Collection<SBuildType> myAffectedBuildTypes;

    public TestConnectionException(@NotNull Throwable cause, @NotNull Collection<SBuildType> affectedBuildTypes) {
      super(cause);
      myAffectedBuildTypes = affectedBuildTypes;
    }

    @NotNull
    public Collection<SBuildType> getAffectedBuildTypes() {
      return myAffectedBuildTypes;
    }
  }
}
