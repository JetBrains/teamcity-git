

package jetbrains.buildServer.buildTriggers.vcs.git.patch;

import com.jcraft.jsch.JSch;
import java.io.*;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitRepoOperationsImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleFetchException;
import jetbrains.buildServer.serverSide.CachePaths;
import jetbrains.buildServer.serverSide.impl.ssh.ServerSshKnownHostsManagerImpl;
import jetbrains.buildServer.ssh.SshKnownHostsManager;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.jsch.JSchConfigInitializer;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsUtil;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.vcs.patches.PatchBuilderImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitPatchProcess {

  public static void main(String... args) throws Exception {
    Map<String, String> properties = VcsUtil.stringToProperties(GitServerUtil.readInput());
    GitPatchProcessSettings settings = new GitPatchProcessSettings(properties);
    GitServerUtil.configureInternalProperties(settings.getInternalProperties());
    GitServerUtil.configureStreamFileThreshold(Integer.MAX_VALUE);
    GitServerUtil.configureExternalProcessLogger(settings.isDebugEnabled());
    GitServerUtil.setupMemoryMappedIndexReading();
    JSchConfigInitializer.initJSchConfig(JSch.class);

    PluginConfigImpl config = new PluginConfigImpl(new ConstantCachePaths(settings.getGitCachesDir()));
    RepositoryManager repositoryManager = new RepositoryManagerImpl(
      config, new MirrorManagerImpl(config, new HashCalculatorImpl(), new RemoteRepositoryUrlInvestigatorImpl()));
    GitMapFullPath mapFullPath = new GitMapFullPath(config, new RevisionsCache(config));
    VcsRootSshKeyManager sshKeyManager = new ConstantSshKeyManager(settings.getKeyBytes());
    SshKnownHostsManager knownHostsManager = new ServerSshKnownHostsManagerImpl(null);
    TransportFactory transportFactory = new TransportFactoryImpl(config, sshKeyManager, settings.getGitTrustStoreProvider(), knownHostsManager);
    FetcherProperties fetcherProperties = new FetcherProperties(config);
    FetchCommand fetchCommand = new FetchCommandImpl(config, transportFactory, fetcherProperties, sshKeyManager, settings.getGitTrustStoreProvider());
    GitRepoOperations repoOperations = new GitRepoOperationsImpl(config, transportFactory, sshKeyManager, fetchCommand, knownHostsManager);
    CommitLoader commitLoader = new CommitLoaderImpl(repositoryManager, repoOperations, mapFullPath, config, new FetchSettingsFactoryImpl());

    OperationContext context = new OperationContext(commitLoader, repositoryManager, settings.getRoot(), "build patch", GitProgress.NO_OP, config, null);
    OutputStream fos = new BufferedOutputStream(new FileOutputStream(settings.getPatchFile()));
    try {
      PatchBuilderImpl patchBuilder = new PatchBuilderImpl(fos);
      new GitPatchBuilder(context,
                          patchBuilder,
                          settings.getFromRevision(),
                          settings.getToRevision(),
                          settings.getCheckoutRules(),
                          settings.isVerboseTreeWalkLog(),
                          new PrintFile(), transportFactory).buildPatch();
      patchBuilder.close();
    } catch (Throwable t) {
      if (settings.isDebugEnabled() || isImportant(t)) {
        System.err.println(t.getMessage());
        t.printStackTrace(System.err);
      } else {
        String msg = t.getMessage();
        boolean printStackTrace = false;
        if (t instanceof SubmoduleFetchException) {
          Throwable cause = t.getCause();
          printStackTrace = cause != null && isImportant(cause);
        }
        System.err.println(msg);
        if (printStackTrace)
          t.printStackTrace(System.err);
      }
      System.exit(1);
    } finally {
      fos.close();
    }
  }


  private static class ConstantCachePaths implements CachePaths {
    private final File myCachesDir;
    public ConstantCachePaths(@NotNull File cachesDir) {
      myCachesDir = cachesDir;
    }

    @NotNull
    public File getCacheDirectory(@NotNull final String name) {
      return myCachesDir;
    }
  }


  private static class ConstantSshKeyManager implements VcsRootSshKeyManager {
    private final byte[] myKeyBytes;

    public ConstantSshKeyManager(@Nullable byte[] keyBytes) {
      myKeyBytes = keyBytes;
    }

    @Nullable
    public TeamCitySshKey getKey(@NotNull VcsRoot root) {
      if (myKeyBytes == null)
        return null;
      return new TeamCitySshKey(""/*doesn't matter*/, myKeyBytes, false/*doesn't matter*/);
    }
  }


  private static class GitPatchProcessSettings {
    private final File myInternalProperties;
    private final boolean myVerboseTreeWalkLog;
    private final String myFromRevision;
    private final String myToRevision;
    private final CheckoutRules myCheckoutRules;
    private final File myGitCachesDir;
    private final File myPatchFile;
    private final byte[] myKeyBytes;
    private final boolean myDebugEnabled;
    private final VcsRoot myRoot;
    private final GitTrustStoreProvider myGitTrustStoreProvider;

    public GitPatchProcessSettings(@NotNull Map<String, String> props) {
      myInternalProperties = readInternalProperties(props);
      myVerboseTreeWalkLog = readVerboseTreeLog(props);
      myFromRevision = readFromRevision(props);
      myToRevision = readToRevision(props);
      myCheckoutRules = readCheckoutRules(props);
      myGitCachesDir = readGitCachesDir(props);
      myPatchFile = readPatchFile(props);
      myKeyBytes = readKeyBytes(props);
      myDebugEnabled = readDebugEnabled(props);
      myRoot = readRoot(props);
      myGitTrustStoreProvider = readGitTrustStoreProvider(props);
    }

    @NotNull
    private File readInternalProperties(@NotNull Map<String, String> props) {
      String path = props.remove(Constants.FETCHER_INTERNAL_PROPERTIES_FILE);
      if (path == null)
        throw new IllegalArgumentException("internal.properties file is not specified");
      return new File(path);
    }

    private boolean readVerboseTreeLog(@NotNull Map<String, String> props) {
      return Boolean.valueOf(props.remove("patcher.verboseTreeWalkLog"));
    }

    private String readFromRevision(@NotNull Map<String, String> props) {
      return props.remove(Constants.PATCHER_FROM_REVISION);
    }

    private String readToRevision(@NotNull Map<String, String> props) {
      String result = props.remove(Constants.PATCHER_TO_REVISION);
      if (result == null)
        throw new IllegalArgumentException("toRevision is not specified");
      return result;
    }

    @NotNull
    private CheckoutRules readCheckoutRules(@NotNull Map<String, String> props) {
      String result = props.remove(Constants.PATCHER_CHECKOUT_RULES);
      if (result == null)
        throw new IllegalArgumentException("checkout rules are not specified");
      return new CheckoutRules(result);
    }

    @NotNull
    private File readGitCachesDir(@NotNull Map<String, String> props) {
      String result = props.remove(Constants.PATCHER_CACHES_DIR);
      if (result == null)
        throw new IllegalArgumentException("git caches dir is not specified");
      return new File(result);
    }

    @NotNull
    private File readPatchFile(@NotNull Map<String, String> props) {
      String result = props.remove(Constants.PATCHER_PATCH_FILE);
      if (result == null)
        throw new IllegalArgumentException("patch file is not specified");
      return new File(result);
    }

    private byte[] readKeyBytes(@NotNull Map<String, String> props) {
      String result = props.remove(Constants.PATCHER_UPLOADED_KEY);
      if (result == null)
        return null;
      try {
        return result.getBytes("UTF-8");
      } catch (UnsupportedEncodingException e) {
        return null;
      }
    }

    private boolean readDebugEnabled(final Map<String, String> props) {
      return "true".equals(props.remove(Constants.VCS_DEBUG_ENABLED));
    }

    @NotNull
    private VcsRoot readRoot(@NotNull Map<String, String> props) {
      return new VcsRootImpl(0, props);
    }

    @NotNull
    private GitTrustStoreProvider readGitTrustStoreProvider(@NotNull Map<String, String> props) {
      return new GitTrustStoreProviderStatic(props.get(Constants.GIT_TRUST_STORE_PROVIDER));
    }


    @NotNull
    public File getInternalProperties() {
      return myInternalProperties;
    }

    public boolean isVerboseTreeWalkLog() {
      return myVerboseTreeWalkLog;
    }

    public String getFromRevision() {
      return myFromRevision;
    }

    @NotNull
    public String getToRevision() {
      return myToRevision;
    }

    @NotNull
    public CheckoutRules getCheckoutRules() {
      return myCheckoutRules;
    }

    @NotNull
    public File getGitCachesDir() {
      return myGitCachesDir;
    }

    @NotNull
    public File getPatchFile() {
      return myPatchFile;
    }

    @Nullable
    public byte[] getKeyBytes() {
      return myKeyBytes;
    }

    public boolean isDebugEnabled() {
      return myDebugEnabled;
    }

    @NotNull
    public VcsRoot getRoot() {
      return myRoot;
    }

    @NotNull
    public GitTrustStoreProvider getGitTrustStoreProvider() {
      return myGitTrustStoreProvider;
    }
  }

  private static boolean isImportant(Throwable t) {
    return t instanceof NullPointerException ||
           t instanceof Error ||
           t instanceof InterruptedException ||
           t instanceof InterruptedIOException;
  }


  private final static class PrintFile extends PatchFileAction {
    @Override
    void call(@NotNull final String action, @NotNull final String file) {
      System.out.println(action + " " + file);
    }
  }
}