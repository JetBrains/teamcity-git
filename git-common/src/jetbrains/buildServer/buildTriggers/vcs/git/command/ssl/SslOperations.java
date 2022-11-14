package jetbrains.buildServer.buildTriggers.vcs.git.command.ssl;

import java.io.File;
import java.io.IOException;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitFacade;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.TrustStoreIO;
import org.apache.commons.codec.CharEncoding;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class SslOperations {
  public final static String CERT_DIR = "ssl";
  public final static String CERT_FILE = "git_custom_certificates.crt";

  private final static Logger LOG = Logger.getLogger(SslOperations.class);

  public static void deleteSslOption(@NotNull final GitFacade gitFacade) {
    try {
      final String previous = gitFacade.getConfig().setPropertyName("http.sslCAInfo").callWithIgnoreExitCode();
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
