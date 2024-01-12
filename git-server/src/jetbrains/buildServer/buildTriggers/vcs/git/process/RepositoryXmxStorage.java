

package jetbrains.buildServer.buildTriggers.vcs.git.process;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.ProcessXmxProvider;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.PropertiesUtil;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * {@link ProcessXmxProvider.XmxStorage} implementation which stores the values inside {@link Repository#getDirectory()} folder
 */
public class RepositoryXmxStorage implements ProcessXmxProvider.XmxStorage {

  public static final String XMX = ".Xmx";
  public static final String SUFFIX = "M";

  private static Logger LOG = Logger.getInstance(RepositoryXmxStorage.class.getName());

  @NotNull final File myStorage;
  @NotNull final String myProcessName;

  public RepositoryXmxStorage(@NotNull final Repository repository, final String processName) {
    this(repository.getDirectory(), processName);
  }

  public RepositoryXmxStorage(@NotNull final File repoDir, @NotNull final String processName) {
    myStorage = new File(repoDir, "teamcity.properties");
    myProcessName = processName;
  }

  @NotNull
  public File getStorage() {
    return myStorage;
  }

  @Nullable
  @Override
  public Integer read() {
    if (!myStorage.isFile()) return null;

    final Properties properties;
    try {
      properties = PropertiesUtil.loadProperties(myStorage);
    } catch (IOException e) {
      LOG.warn("Failed to read " + myProcessName + " -Xmx value from " + myStorage.getAbsolutePath(), e);
      return null;
    }

    final String value = properties.getProperty(getPropertyKey());
    if (value == null) return null;
    if (value.endsWith(SUFFIX)) {
      try {
        return Integer.parseInt(value.substring(0, value.length() - SUFFIX.length()));
      } catch (NumberFormatException ignored) {
        // report below
      }
    }
    LOG.warn("Failed to parse " + myProcessName + " -Xmx value \"" + value + "\" from " + myStorage.getAbsolutePath());
    return null;
  }

  @NotNull
  private String getPropertyKey() {
    return myProcessName + XMX;
  }

  @Override
  public void write(@Nullable final Integer xmx) {
    final Properties properties;
    try {
      properties = PropertiesUtil.loadProperties(myStorage);
    } catch (IOException e) {
      LOG.warn("Failed to read " + myProcessName + " -Xmx value from " + myStorage.getAbsolutePath(), e);
      return;
    }

    if (xmx == null && properties.isEmpty()) return;

    final String key = getPropertyKey();
    final String oldValue = properties.getProperty(key);

    if (xmx == null && oldValue == null) return;

    String newValue;
    if (xmx == null) {
      newValue = null;
    } else {
      newValue = xmx + SUFFIX;
      if (newValue.equals(oldValue)) return;
    }

    FileUtil.delete(myStorage);

    if (newValue == null) {
      properties.remove(key);
    } else {
      properties.setProperty(key, newValue);
    }

    if (properties.isEmpty()) return;

    try {
      PropertiesUtil.storeProperties(properties, myStorage, null);
    } catch (IOException e) {
      LOG.warn("Failed to write " + myProcessName + " -Xmx value \"" + xmx + "\" to " + myStorage.getAbsolutePath(), e);
      FileUtil.delete(myStorage);
    }
  }
}