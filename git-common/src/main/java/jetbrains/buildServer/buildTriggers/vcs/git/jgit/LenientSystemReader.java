package jetbrains.buildServer.buildTriggers.vcs.git.jgit;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.PostConstruct;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;

/**
 * Copy of {@link org.eclipse.jgit.util.SystemReader.Default} that makes errors with JGit's own config file non-fatal.
 */
public class LenientSystemReader extends SystemReader {

  private static final Logger LOG = Logger.getInstance(LenientSystemReader.class);

  @PostConstruct
  public void register() {
    LOG.debug("Registering LenientSystemReader as JGit system reader.");
    SystemReader.setInstance(this);
  }

  public void ensureRegistered() {
    final SystemReader currentSystemReader = SystemReader.getInstance();
    if (!(currentSystemReader instanceof LenientSystemReader)) {
      if (currentSystemReader != null) {
        LOG.warn("Unexpected JGit system reader type registered: " + currentSystemReader.getClass().getName() + " overriding with LenientSystemReader.");
      }
      register();
    }
  }

  public FS getFS() {
    return FS.DETECTED;
  }

  private volatile String myHostname;

  public String getenv(String variable) {
    return System.getenv(variable);
  }

  public String getProperty(String key) {
    return System.getProperty(key);
  }

  public FileBasedConfig openSystemConfig(Config parent, FS fs) {
    if (StringUtils.isEmptyOrNull(getenv("GIT_CONFIG_NOSYSTEM"))) {
      File configFile = fs.getGitSystemConfig();
      if (configFile != null) {
        return new FileBasedConfig(parent, configFile, fs);
      }
    }

    return new FileBasedConfig(parent, null, fs) {
      public void load() {
      }

      public boolean isOutdated() {
        return false;
      }
    };
  }

  public FileBasedConfig openUserConfig(Config parent, FS fs) {
    return new FileBasedConfig(parent, new File(fs.userHome(), ".gitconfig"), fs);
  }

  private Path getXDGConfigHome(FS fs) {
    String configHomePath = getenv("XDG_CONFIG_HOME");
    if (StringUtils.isEmptyOrNull(configHomePath)) {
      configHomePath = (new File(fs.userHome(), ".config")).getAbsolutePath();
    }

    try {
      return Paths.get(configHomePath);
    } catch (InvalidPathException e) {
      LOG.warnAndDebugDetails("Environment variable XDG_CONFIG_HOME contains an invalid path " + configHomePath, e);
      return null;
    }
  }

  public FileBasedConfig openJGitConfig(Config parent, FS fs) {
    Path xdgPath = getXDGConfigHome(fs);
    if (xdgPath != null) {
      Path configPath = xdgPath.resolve("jgit").resolve("config");
      return new LenientConfig(parent, configPath.toFile(), fs);
    } else {
      return new LenientConfig(parent, new File(fs.userHome(), ".jgitconfig"), fs);
    }
  }

  public String getHostname() {
    if (myHostname == null) {
      try {
        InetAddress localMachine = InetAddress.getLocalHost();
        myHostname = localMachine.getCanonicalHostName();
      } catch (UnknownHostException var2) {
        myHostname = "localhost";
      }

      assert myHostname != null;
    }

    return myHostname;
  }

  public long getCurrentTime() {
    return System.currentTimeMillis();
  }

  public int getTimezone(long when) {
    return getTimeZone().getOffset(when) / '\uea60';
  }

  private static class LenientConfig extends FileBasedConfig {
    public LenientConfig(Config base, File cfgLocation, FS fs) {
      super(base, cfgLocation, fs);
    }

    @Override
    public void load() {
      try {
        super.load();
      } catch (IOException | ConfigInvalidException e) {
        LOG.warnAndDebugDetails("Error loading JGit config file " + getFile().getAbsolutePath() + " the config will be ignored", e);
      }
    }
  }
}
