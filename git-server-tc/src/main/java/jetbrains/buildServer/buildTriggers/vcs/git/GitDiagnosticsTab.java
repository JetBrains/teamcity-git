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
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.audit.ActionType;
import jetbrains.buildServer.serverSide.audit.AuditLogFactory;
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
import org.jdom2.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

public class GitDiagnosticsTab extends DiagnosticTab {

  private static final String FILENAME_PREFIX = "testConnection_" + BuildProject.ROOT_PROJECT_ID + "_";
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();
  private static final Logger LOG = Logger.getInstance(GitDiagnosticsTab.class.getName());
  private static final String CONNECTION_SUCCESSFUL = "No native Git connection errors found";

  private final GitVcsSupport myVcsSupport;
  private final GitRepoOperations myOperations;
  private final GitMainConfigProcessor myMainConfigProcessor;
  private final ProjectManager myProjectManager;
  private final ExecutorServices myExecutors;
  private final TeamCityNodes myNodes;
  private final ServerResponsibility myServerResponsibility;
  private final File myTestConnectionResultsFolder;

  private final Striped<Lock> myLocks = Striped.lazyWeakLock(24);
  private final Map<String, TestConnectionTask> myTestConnectionsInProgress = new HashMap<>();
  private final Map<String, TestConnectionTask> myTestConnectionsFinished = new HashMap<>();
  private final AuditLogFactory myAuditLogFactory;

  public GitDiagnosticsTab(@NotNull PagePlaces pagePlaces,
                           @NotNull WebControllerManager controllerManager,
                           @NotNull PluginDescriptor pluginDescriptor,
                           @NotNull GitVcsSupport vcsSupport,
                           @NotNull GitRepoOperations gitOperations,
                           @NotNull GitMainConfigProcessor mainConfigProcessor,
                           @NotNull ProjectManager projectManager,
                           @NotNull ExecutorServices executorServices,
                           @NotNull TeamCityNodes nodes,
                           @NotNull ServerPaths serverPaths,
                           @NotNull BuildServerEx server,
                           AuditLogFactory auditLogFactory) {
    super(pagePlaces, "gitStatus", "Git");
    myVcsSupport = vcsSupport;
    myOperations = gitOperations;
    myMainConfigProcessor = mainConfigProcessor;
    myProjectManager = projectManager;
    myExecutors = executorServices;
    myNodes = nodes;
    myServerResponsibility = server.getServerResponsibility();
    myTestConnectionResultsFolder = serverPaths.getCacheDirectory("git/testConnectionResults");
    myAuditLogFactory = auditLogFactory;

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
              // on page load we first try to load saved previous results for root project
              if (project.isRootProject() && "true".equals(request.getParameter("loadStored"))) {
                final File storedFile = getExitingStoredTestConnectionErrorsFile();
                if (storedFile == null) {
                  // this request tries to load previous results for root project, but there is nothing found
                  return null;
                } else {
                  final Map<VcsRootLink, List<TestConnectionError>> testConnectionErrors = getStoredTestConnectionErrors(storedFile);
                  if (testConnectionErrors == null) {
                    // this request tries to load previous results for root project, but we failed to read from file
                    return null;
                  } else {
                    final Map<String, Object> model = new HashMap<>();
                    model.put("testConnectionErrors", testConnectionErrors);
                    model.put("testConnectionProject", project);
                    model.put("testConnectionTimestamp", getTimestampFromStoredFile(storedFile).toString());
                    model.put("testConnectionInProgress", false);
                    model.put("testConnectionStopped", false);
                    model.put("testConnectionStatus", testConnectionErrors.isEmpty() ? CONNECTION_SUCCESSFUL : "");
                    return new ModelAndView(pluginDescriptor.getPluginResourcesPath("testConnectionErrors.jsp"), model);
                  }
                }
              }

              Map<VcsRootLink, List<TestConnectionError>> testConnectionErrors = null;
              boolean testConnectionInProgress = false;
              boolean testConnectionStopped = Boolean.parseBoolean(request.getParameter("stopTestConnection"));
              String testConnectionStatus = "";
              Date timestamp = Dates.now();

              final Lock lock = myLocks.get(projectExternalId);
              lock.lock();
              try {
                TestConnectionTask task = myTestConnectionsFinished.get(projectExternalId);
                if (task == null) {
                  testConnectionInProgress = !testConnectionStopped;
                  task = myTestConnectionsInProgress.get(projectExternalId);
                  if (task == null) {
                    if (testConnectionInProgress) {
                      final List<VcsRootInstance> vcsRootInstances = project.getVcsRootInstances();
                      task = new TestConnectionTask(timestamp, vcsRootInstances.size());
                      myTestConnectionsInProgress.put(projectExternalId, task);

                      executorServices.getLowPriorityExecutorService().execute(() -> runTestConnectionForAllProjectRoots(project, vcsRootInstances, Dates.now()));
                    }
                  } else if (testConnectionStopped) {
                    task.markCanceled();
                    finishTask(projectExternalId);
                  }
                }
                if (task != null) {
                  testConnectionErrors = new LinkedHashMap<>(task.getErrors());
                  testConnectionStatus = task.getStatusString();
                  timestamp = task.getTimestamp();
                  testConnectionStopped = task.isCanceled();
                }
              } finally {
                lock.unlock();
              }

              final Map<String, Object> model = new HashMap<>();
              model.put("testConnectionErrors", testConnectionErrors);
              model.put("testConnectionProject", project);
              model.put("testConnectionTimestamp", timestamp.toString());
              model.put("testConnectionInProgress", testConnectionInProgress);
              model.put("testConnectionStopped", testConnectionStopped);
              model.put("testConnectionStatus", testConnectionStatus);
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
              boolean nativeGitOperationsEnabled = "enable".equalsIgnoreCase(request.getParameter("switchNativeGit"));
              final boolean enabled = myMainConfigProcessor.setNativeGitOperationsEnabled(nativeGitOperationsEnabled);
              xmlResponse.addContent(new Element("nativeGitOperationsEnabled").addContent(String.valueOf(enabled)));
              if (enabled)
                myAuditLogFactory.createForServer().log(ActionType.NATIVE_GIT_ENABLED, null, null, SessionUser.getUser(request));
              else
                myAuditLogFactory.createForServer().log(ActionType.NATIVE_GIT_DISABLED, null, null, SessionUser.getUser(request));
            }
          });
        }
        return null;
      }
    });
  }

  public static boolean isEnabled() {
    return TeamCityProperties.getBooleanOrTrue("teamcity.git.diagnosticsTab.enabled");
  }

  private void runTestConnectionForAllProjectRoots(@NotNull SProject project, @NotNull List<VcsRootInstance> vcsRootInstances, @NotNull Date timestamp) {
    final String externalId = project.getExternalId();
    boolean canceled = false;
    try {
      final long start = System.currentTimeMillis();
      int processed = 0;

      final CachingNativeGitTestConnectionRunner testConnectionRunner = new CachingNativeGitTestConnectionRunner(myVcsSupport);
      for (VcsRootInstance ri : vcsRootInstances) {
        ++processed;
        if (!isGitRoot(ri) || ri.getUsages().isEmpty()) {
          continue;
        }
        final String error = testConnectionRunner.testConnection(ri);
        final Lock lock = myLocks.get(externalId);
        lock.lock();
        try {
          final VcsRootLink vcsRootLink = new VcsRootLink(ri.getParent());
          final TestConnectionTask task = myTestConnectionsInProgress.get(externalId);
          if (task == null) {
            canceled = true;
            break;
          }
          task.setRootsProcessed(processed);
          if (error == null) continue;

          final List<TestConnectionError> rootErrors = task.getRootErrors(vcsRootLink);
          rootErrors.add(new TestConnectionError(error, ri.getUsages().keySet().stream().map(bt -> new BuildTypeLink(bt)).collect(Collectors.toSet())));
        } finally {
          lock.unlock();
        }
      }

      final long duration = System.currentTimeMillis() - start;
      LOG.info("Native Git TestConnection for " + LogUtil.describe(project) + (canceled ? " was canceled," : " finished,") + " took " + TimeUnit.MILLISECONDS.toSeconds(duration) + " sec, processed " + testConnectionRunner.getProcessed() + " Git VCS root instances, cache size: " + testConnectionRunner.getCacheSize() + ", cache hits: " + testConnectionRunner.getCacheHits());
      testConnectionRunner.dispose();

      if (canceled) return;
      if (project.isRootProject()) {
        FileUtil.delete(myTestConnectionResultsFolder);

        final File storedFile = getStoredTestConnectionErrorsFile(timestamp);
        deleteFile(storedFile);
        FileUtil.createParentDirs(storedFile);

        FileWriter writer = null;
        try {
          final Map<VcsRootLink, List<TestConnectionError>> errors;
          final Lock lock = myLocks.get(externalId);
          lock.lock();
          try {
            final TestConnectionTask task = myTestConnectionsInProgress.get(externalId);
            if (task == null) return;
            errors = task.getErrors();
          } finally {
            lock.unlock();
          }
          writer = new FileWriter(storedFile);
          GSON.toJson(errors, Map.class, writer);
        } catch (Throwable e) {
          LOG.warnAndDebugDetails("Exception while saving native Git Test Connection results for " + LogUtil.describe(project) + " to " + storedFile, e);
          deleteFile(storedFile);
        } finally {
          FileUtil.close(writer);
        }
      }
    } finally {
      final Lock lock = myLocks.get(externalId);
      lock.lock();
      try {
        finishTask(externalId);
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
    final File[] files = FileUtil.listFiles(myTestConnectionResultsFolder, (d, n) -> n.startsWith(FILENAME_PREFIX) && n.endsWith(".json"));
    return files.length == 0 ? null : files[0];
  }

  @NotNull
  private File getStoredTestConnectionErrorsFile(@NotNull Date timestamp) {
    return new File(myTestConnectionResultsFolder, FILENAME_PREFIX + timestamp.getTime() + ".json");
  }

  private void finishTask(@NotNull String externalId) {
    try {
      final TestConnectionTask task = myTestConnectionsInProgress.remove(externalId);
      if (task == null) return;

      task.markFinished();

      myTestConnectionsFinished.put(externalId, task);
      myExecutors.getNormalExecutorService().schedule(() -> {
        final Lock l = myLocks.get(externalId);
        try {
          myTestConnectionsFinished.remove(externalId);
        } finally {
          l.unlock();
        }
      }, 30, TimeUnit.SECONDS);
    } catch (RejectedExecutionException e) {
      LOG.warnAndDebugDetails("Failed to schedule native Git TestConnection results remove task", e);
      myTestConnectionsFinished.remove(externalId);
    }
  }

  @NotNull
  private Date getTimestampFromStoredFile(@NotNull File file) {
    final String name = file.getName();
    try {
      return new Date(Long.parseLong(name.substring(FILENAME_PREFIX.length(), name.length() - 5)));
    } catch (Throwable e) {
      LOG.warnAndDebugDetails("Failed to parse timestamp from stored native Git Test Connection results file name " + name + ", will remove file", e);
      deleteFile(file);
      return Dates.now();
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

  @Nullable
  private Map<VcsRootLink, List<TestConnectionError>> getStoredTestConnectionErrors(@NotNull File file) {
    if (!file.isFile()) {
      return null;
    }
    LOG.debug("Loading native Git Test Connection results for root project from " + file);
    try {
      return GSON.fromJson(new FileReader(file), Map.class);
    } catch (Throwable e) {
      LOG.warnAndDebugDetails("Exception while parsing stored native Git Test Connection results from " + file + ", will remove the file", e);
      deleteFile(file);
      return null;
    }
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    return super.isAvailable(request) && isEnabled();
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
    super.fillModel(model, request);
    model.put("nativeGitOperationsEnabled", myMainConfigProcessor.isNativeGitOperationsEnabled());
    model.put("isMultinodeSetup", myNodes.getNodes().size() > 1);
    model.put("canManageServerConfiguration", myServerResponsibility.canManageServerConfiguration());
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
    private final Collection<BuildTypeLink> affectedBuildTypes;

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
    private final int myRootsToProcess;
    private int myRootsProcessed;
    private boolean myFinished;
    private boolean myCanceled;
    private final Map<VcsRootLink, List<TestConnectionError>> myErrors = new LinkedHashMap<>();


    private TestConnectionTask(@NotNull Date timestamp, int rootsToProcess) {
      myTimestamp = timestamp;
      myRootsToProcess = rootsToProcess;
      myRootsProcessed = 0;
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

    @NotNull
    public String getStatusString() {
      final long percent = Math.round((1.0 * myRootsProcessed / myRootsToProcess) * 100);
      final String status = myRootsToProcess > 0 && myRootsProcessed > 0 ? (percent == 0 ? "<1" : percent) + "% (processed " + myRootsProcessed + " VCS root instances out of " + myRootsToProcess + ")" : "";
      if (myFinished) {
        if (myCanceled) {
          return status.isEmpty() ? "Cancelled" : "Cancelled: " + status;
        } else if (myErrors.isEmpty()) {
          return CONNECTION_SUCCESSFUL;
        }
        return myRootsProcessed > 0 ? "Processed " + myRootsProcessed + " VCS root instances and detected " + myErrors.size() + " errors" : "Detected " + myErrors.size() + " errors";
      }
      return "In progress" + (status.isEmpty() ? "" : ": " + status);
    }

    public void setRootsProcessed(int rootsProcessed) {
      myRootsProcessed = rootsProcessed;
    }

    public void markCanceled() {
      myCanceled = true;
    }

    public boolean isCanceled() {
      return myCanceled;
    }

    public boolean isFinished() {
      return myFinished;
    }

    public void markFinished() {
      myFinished = true;
    }
  }
}
