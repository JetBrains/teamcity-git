package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitNativeOperationsStatus;
import jetbrains.buildServer.serverSide.MainConfigProcessor;
import jetbrains.buildServer.serverSide.impl.MainConfigManager;
import org.jdom2.Element;

public class GitMainConfigProcessor implements MainConfigProcessor, GitNativeOperationsStatus {

  private static final String NATIVE_OPERATIONS_ENABLED = "nativeOperationsEnabled";
  private static final String GIT_ELEMENT = "git";

  private final MainConfigManager myMainConfigManager;
  private boolean myNativeGitOperationsEnabled = false;

  public GitMainConfigProcessor(MainConfigManager mainConfigManager) {
    myMainConfigManager = mainConfigManager;
  }

  @Override
  public void writeTo(Element parentElement) {
    if (myNativeGitOperationsEnabled) {
      final Element gitElement = new Element(GIT_ELEMENT);
      gitElement.setAttribute(NATIVE_OPERATIONS_ENABLED, "true");
      parentElement.addContent(gitElement);
    }
  }

  @Override
  public void readFrom(Element rootElement) {
    final Element gitElement = rootElement.getChild(GIT_ELEMENT);
    if (gitElement == null) {
      myNativeGitOperationsEnabled = false;
    } else {
      final String enabled = gitElement.getAttributeValue(NATIVE_OPERATIONS_ENABLED);
      myNativeGitOperationsEnabled = Boolean.parseBoolean(enabled);
    }
  }

  public boolean isNativeGitOperationsEnabled() {
    return myNativeGitOperationsEnabled;
  }

  public boolean setNativeGitOperationsEnabled(boolean nativeGitOperatoinsEnabled) {
    myNativeGitOperationsEnabled = nativeGitOperatoinsEnabled;
    myMainConfigManager.persistConfiguration();
    return myNativeGitOperationsEnabled;
  }
}
