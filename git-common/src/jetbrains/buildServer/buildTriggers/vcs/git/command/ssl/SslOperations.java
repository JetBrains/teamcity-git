package jetbrains.buildServer.buildTriggers.vcs.git.command.ssl;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitFacade;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class SslOperations {
  public final static String CERT_DIR = "ssl";
  public final static String CERT_FILE = "git_custom_certificates.crt";

  private final static Logger LOG = Logger.getLogger(SslOperations.class);

  @NotNull
  public static String getCertPath(@NotNull final GitFacade gitFacade) throws VcsException {
    return gitFacade.gitConfig().setPropertyName("http.sslCAInfo").callWithIgnoreExitCode();
  }

  public static void deleteSslOption(@NotNull final GitFacade gitFacade) {
    try {
      final String previous = getCertPath(gitFacade);
      if (!StringUtil.isEmptyOrSpaces(previous)) {
        /* do not need custom certificate then remove corresponding options if exists */
        gitFacade.setConfig().setPropertyName("http.sslCAInfo").unSet().call();
      }
    } catch (Exception e) {
      /* option was not exist, ignore exception then */
    }
  }

  public static void setSslOption(@NotNull final GitFacade gitFacade, @NotNull final String path) {
    try {
      gitFacade.setConfig().setPropertyName("http.sslCAInfo").setValue(path).call();
    } catch (Exception e) {
      LOG.error("Error while setting sslCAInfo git option", e);
    }
  }
}
