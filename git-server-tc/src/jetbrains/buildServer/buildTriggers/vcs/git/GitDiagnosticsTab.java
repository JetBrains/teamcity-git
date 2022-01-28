package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.diagnostic.web.DiagnosticTab;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

public class GitDiagnosticsTab extends DiagnosticTab {

  private final GitRepoOperations myOperations;
  private final GitMainConfigProcessor myMainConfigProcessor;

  public GitDiagnosticsTab(@NotNull PagePlaces pagePlaces,
                           @NotNull PluginDescriptor pluginDescriptor,
                           @NotNull GitRepoOperations gitOperations,
                           @NotNull GitMainConfigProcessor mainConfigProcessor) {
    super(pagePlaces, "gitStatus", "Git");
    myOperations = gitOperations;
    myMainConfigProcessor = mainConfigProcessor;
    setPermission(Permission.MANAGE_SERVER_INSTALLATION);
    setIncludeUrl(pluginDescriptor.getPluginResourcesPath("gitStatusTab.jsp"));
    register();
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
    } catch (VcsException e) {
      model.put("gitExecError", e);
      model.put("isGitExecError", true);
      model.put("nativeGitOperationsSupported", false);
    }
  }
}
