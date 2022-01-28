package jetbrains.buildServer.buildTriggers.vcs.git;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitNativeOperationsStatus;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

public class GitStatusController extends BaseFormXmlController {
  private final GitNativeOperationsStatus myNativeOperationsStatus;

  public GitStatusController(@NotNull WebControllerManager webControllerManager,
                             @NotNull GitNativeOperationsStatus nativeOperationsStatus) {
    webControllerManager.registerController("/admin/diagnostic/nativeGitStatus.html", this);
    myNativeOperationsStatus = nativeOperationsStatus;
  }

  @Override
  protected ModelAndView doGet(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
    return null;
  }

  @Override
  protected void doPost(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {
    // TODO: check permissions
    final boolean enabled = myNativeOperationsStatus.setNativeGitOperationsEnabled("enable".equalsIgnoreCase(request.getParameter("switchNativeGit")));
    xmlResponse.addContent(new Element("nativeGitOperationsEnabled").addContent(String.valueOf(enabled)));
  }
}
