package jetbrains.buildServer.buildTriggers.vcs.git;

import com.google.common.util.concurrent.Striped;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.AjaxRequestProcessor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.XmlResponseUtil;
import jetbrains.buildServer.diagnostic.web.DiagnosticTab;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.FileUtil;
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

  private static final String FILENAME_PREFIX = "testConnection_" + BuildProject.ROOT_PROJECT_ID + "_";
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();
  private static final Logger LOG = Logger.getInstance(GitDiagnosticsTab.class.getName());

  private final GitVcsSupport myVcsSupport;
  private final GitRepoOperations myOperations;
  private final GitMainConfigProcessor myMainConfigProcessor;
  private final ProjectManager myProjectManager;
  private final ExecutorServices myExecutors;

  private final Striped<Lock> myLocks = Striped.lazyWeakLock(24);
  private final Map<String, TestConnectionTask> myTestConnectionsInProgress = new HashMap<>();
  private final Map<String, TestConnectionTask> myTestConnectionsFinished = new HashMap<>();

  public GitDiagnosticsTab(@NotNull PagePlaces pagePlaces,
                           @NotNull WebControllerManager controllerManager,
                           @NotNull PluginDescriptor pluginDescriptor,
                           @NotNull GitVcsSupport vcsSupport,
                           @NotNull GitRepoOperations gitOperations,
                           @NotNull GitMainConfigProcessor mainConfigProcessor,
                           @NotNull ProjectManager projectManager,
                           @NotNull ExecutorServices executorServices) {
    super(pagePlaces, "gitStatus", "Git");
    myVcsSupport = vcsSupport;
    myOperations = gitOperations;
    myMainConfigProcessor = mainConfigProcessor;
    myProjectManager = projectManager;
    myExecutors = executorServices;

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
            if ("ALL".equals(vcsRoots)) {
              Map<VcsRootLink, List<TestConnectionError>> testConnectionErrors = null;
              Date timestamp = Dates.now();
              if (project.isRootProject()) {
                // on page load we first try to load saved previous results for root project
                final boolean loadStored = "true".equals(request.getParameter("loadStored"));
                if (loadStored) {
                  final File storedFile = getExitingStoredTestConnectionErrorsFile();
                  if (storedFile == null) {
                    // this request tries to load previous results for root project, but there is nothing found
                    return null;
                  } else {
                    testConnectionErrors = getStoredTestConnectionErrors(storedFile);
                    if (testConnectionErrors != null) {
                      timestamp = getTimestampFromStoredFile(storedFile);
                    }
                  }
                }
              }
              final boolean testConnectionInProgress;
              if (testConnectionErrors == null) {
                final Lock lock = myLocks.get(projectExternalId);
                lock.lock();
                try {
                  TestConnectionTask task;
                  task = myTestConnectionsFinished.get(projectExternalId);
                  if (task == null) {
                    testConnectionInProgress = true;

                    task = myTestConnectionsInProgress.get(projectExternalId);
                    if (task == null) {
                      final Date taskTimestamp = timestamp;
                      task = new TestConnectionTask(timestamp);
                      myTestConnectionsInProgress.put(projectExternalId, task);

                      executorServices.getLowPriorityExecutorService().execute(() -> runTestConnectionForAllProjectRoots(project, taskTimestamp));
                    }
                  } else {
                    testConnectionInProgress = false;
                  }
                  timestamp = task.getTimestamp();
                  testConnectionErrors = new LinkedHashMap<>(task.getErrors());
                } finally {
                  lock.unlock();
                }
              } else {
                testConnectionInProgress = false;
              }

              final Map<String, Object> model = new HashMap<>();
              model.put("testConnectionErrors", testConnectionErrors);
              model.put("testConnectionProject", project);
              model.put("testConnectionTimestamp", timestamp.toString());
              model.put("testConnectionInProgress", testConnectionInProgress);
              return new ModelAndView(pluginDescriptor.getPluginResourcesPath("testConnectionErrors.jsp"), model);
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

  private void runTestConnectionForAllProjectRoots(@NotNull SProject project, @NotNull Date timestamp) {
    final String externalId = project.getExternalId();
    try {
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
          final Lock lock = myLocks.get(externalId);
          lock.lock();
          try {
            final VcsRootLink vcsRootLink = new VcsRootLink(ri.getParent());
            final List<TestConnectionError> rootErrors = myTestConnectionsInProgress.get(externalId).getRootErrors(vcsRootLink);
            rootErrors.add(new TestConnectionError(e.getMessage(), ri.getUsages().keySet().stream().map(bt -> new BuildTypeLink(bt)).collect(Collectors.toSet())));
          } finally {
            lock.unlock();
          }
        }
      }
      if (project.isRootProject()) {
        FileUtil.delete(getTestConnectionResultsFolder());

        final File storedFile = getStoredTestConnectionErrorsFile(timestamp);
        deleteFile(storedFile);
        FileUtil.createParentDirs(storedFile);
        FileWriter writer = null;
        try {
          final Map<VcsRootLink, List<TestConnectionError>> errors;
          final Lock lock = myLocks.get(externalId);
          lock.lock();
          try {
            errors = myTestConnectionsInProgress.get(externalId).getErrors();
          } finally {
            lock.unlock();
          }
          writer = new FileWriter(storedFile);
          GSON.toJson(errors, Map.class, writer);
        } catch (Throwable e) {
          LOG.warnAndDebugDetails("Exception while saving native git Test Connection results to " + storedFile, e);
          deleteFile(storedFile);
        } finally {
          FileUtil.close(writer);
        }
      }
    } finally {
      final Lock lock = myLocks.get(externalId);
      lock.lock();
      try {
        myTestConnectionsFinished.put(externalId, myTestConnectionsInProgress.remove(externalId));
        myExecutors.getNormalExecutorService().schedule(() -> {
          final Lock l = myLocks.get(externalId);
          try {
            myTestConnectionsFinished.remove(externalId);
          } finally {
            l.unlock();
          }
        }, 30, TimeUnit.SECONDS);
      } catch (RejectedExecutionException e) {
        LOG.warnAndDebugDetails("Failed to schedule native git TestConnection results remove task", e);
      } finally {
        lock.unlock();
      }
    }
  }

  private static void deleteFile(@Nullable File file) {
    if (file != null) FileUtil.delete(file);
  }

  @Nullable
  private File getExitingStoredTestConnectionErrorsFile() {
    final File[] files = FileUtil.listFiles(getTestConnectionResultsFolder(), (d, n) -> n.startsWith(FILENAME_PREFIX) && n.endsWith(".json"));
    return files.length == 0 ? null : files[0];
  }

  @NotNull
  private File getTestConnectionResultsFolder() {
    return new File(FileUtil.getTempDirectory() + "/gitTestConnection");
  }

  @NotNull
  private File getStoredTestConnectionErrorsFile(@NotNull Date timestamp) {
    return new File(getTestConnectionResultsFolder(), FILENAME_PREFIX + timestamp.getTime() + ".json");
  }

  @NotNull
  private Date getTimestampFromStoredFile(@NotNull File file) {
    final String name = file.getName();
    try {
      return new Date(Long.parseLong(name.substring(FILENAME_PREFIX.length(), name.length() - 5)));
    } catch (Throwable e) {
      LOG.warnAndDebugDetails("Failed to parse timestamp from stored native git Test Connection results file name " + name + ", will remove file", e);
      deleteFile(file);
      return Dates.now();
    }
  }

  @Nullable
  private Map<VcsRootLink, List<TestConnectionError>> getStoredTestConnectionErrors(@NotNull File file) {
    if (!file.isFile()) {
      return null;
    }
    LOG.debug("Loading native git Test Connection results for root project from " + file);
    try {
      return GSON.fromJson(new FileReader(file), Map.class);
    } catch (Throwable e) {
      LOG.warnAndDebugDetails("Exception while parsing stored native git Test Connection results from " + file + ", will remove the file", e);
      deleteFile(file);
      return null;
    }
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

  public static final class TestConnectionError {
    private String message;
    private Collection<BuildTypeLink> affectedBuildTypes;

    public TestConnectionError(@NotNull String message, @NotNull Collection<BuildTypeLink> affectedBuildTypes) {
      this.message = message;
      this.affectedBuildTypes = affectedBuildTypes;
    }

    @NotNull
    public String getMessage() {
      return message;
    }

    public void setMessage(@NotNull String message) {
      this.message = message;
    }

    @NotNull
    public Collection<BuildTypeLink> getAffectedBuildTypes() {
      return affectedBuildTypes;
    }

    public void setAffectedBuildTypes(@NotNull Collection<BuildTypeLink> affectedBuildTypes) {
      this.affectedBuildTypes = affectedBuildTypes;
    }
  }

  public static final class BuildTypeLink {
    private String name;
    private String externalId;

    public BuildTypeLink(@NotNull SBuildType buildType) {
      this(buildType.getName(), buildType.getExternalId());
    }

    public BuildTypeLink(@NotNull String name, @NotNull String externalId) {
      this.name = name;
      this.externalId = externalId;
    }

    @NotNull
    public String getName() {
      return name;
    }

    public void setName(@NotNull String name) {
      this.name = name;
    }

    @NotNull
    public String getExternalId() {
      return externalId;
    }

    public void setExternalId(@NotNull String externalId) {
      this.externalId = externalId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      BuildTypeLink that = (BuildTypeLink)o;

      return externalId.equals(that.externalId);
    }

    @Override
    public int hashCode() {
      return externalId.hashCode();
    }
  }

  public static final class VcsRootLink {
    private String name;
    private String vcsName;
    private String externalId;

    public VcsRootLink(@NotNull VcsRoot vcsRoot) {
      this(vcsRoot.getName(), vcsRoot.getVcsName(), vcsRoot.getExternalId());
    }

    public VcsRootLink(@NotNull String name, @NotNull String vcsName, @NotNull String externalId) {
      this.name = name;
      this.vcsName = vcsName;
      this.externalId = externalId;
    }

    @NotNull
    public String getName() {
      return name;
    }

    public void setName(@NotNull String name) {
      this.name = name;
    }

    @NotNull
    public String getVcsName() {
      return vcsName;
    }

    public void setVcsName(@NotNull String vcsName) {
      this.vcsName = vcsName;
    }

    @NotNull
    public String getExternalId() {
      return externalId;
    }

    public void setExternalId(@NotNull String externalId) {
      this.externalId = externalId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      VcsRootLink that = (VcsRootLink)o;

      return externalId.equals(that.externalId);
    }

    @Override
    public int hashCode() {
      return externalId.hashCode();
    }
  }

  private static final class TestConnectionTask {
    private final Date myTimestamp;
    private final Map<VcsRootLink, List<TestConnectionError>> myErrors = new LinkedHashMap<>();


    private TestConnectionTask(@NotNull Date timestamp) {
      myTimestamp = timestamp;
    }

    @NotNull
    public Date getTimestamp() {
      return myTimestamp;
    }

    @NotNull
    public Map<VcsRootLink, List<TestConnectionError>> getErrors() {
      return myErrors;
    }

    @NotNull
    public List<TestConnectionError> getRootErrors(@NotNull VcsRootLink vcsRootLink) {
      List<TestConnectionError> rootErrors = myErrors.get(vcsRootLink);
      if (rootErrors == null) {
        rootErrors = new CopyOnWriteArrayList<>();
        myErrors.put(vcsRootLink, rootErrors);
      }
      return rootErrors;
    }
  }
}
