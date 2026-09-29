package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.DevelopmentMode;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility to analyze Git remote URL values and detect potentially unsafe local file access.
 * This checker works on raw URL strings accepted by Git, including SCP-like syntax.
 *
 * Reuse this class wherever we need to reason about the nature of a Git remote URL.
 */
public final class GitRemoteUrlInspector {
  private GitRemoteUrlInspector() {}

  private static final Pattern REMOTE_HELPER_PREFIX = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*)::");
  private static final Pattern SCHEME_PREFIX = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*)://");
  private static final Pattern SCP_LIKE_URL = Pattern.compile("^([A-Za-z0-9_][A-Za-z0-9_.-]*@)?[A-Za-z0-9_][A-Za-z0-9_.-]*:(?!:).+$");
  private static final Set<String> DEFAULT_ALLOWED_TRANSPORTS =
    Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("http", "https", "ssh", "git")));

  public enum LocalReason {
    FILE_SCHEME,
    UNIX_ABSOLUTE,
    UNIX_RELATIVE,
    WINDOWS_DRIVE,
    WINDOWS_UNC
  }

  public enum UrlRestriction {
    LOCAL_FILE_ACCESS,
    DISALLOWED_TRANSPORT
  }

  /**
   * Combines the local-file-access check (subject to {@code Constants#ALLOW_FILE_URL}) with the
   * disallowed-transport check. Use this at every checkpoint that must reject unsafe URLs;
   * use {@link #isLocalFileAccess} directly only where the ALLOW_FILE_URL override must be ignored.
   */
  @Nullable
  public static UrlRestriction verifyUrl(@Nullable String rawUrl) {
    if (rawUrl == null) return null;
    String url = rawUrl.trim();

    if (isLocalFileAccess(rawUrl)) {
      return isFileUrlAllowed() ? null : UrlRestriction.LOCAL_FILE_ACCESS;
    }
    if (!isAllowedTransport(url)) {
      return UrlRestriction.DISALLOWED_TRANSPORT;
    }
    return null;
  }

  @NotNull
  public static Set<String> getEffectiveAllowedTransports() {
    Set<String> allowed = new LinkedHashSet<>(DEFAULT_ALLOWED_TRANSPORTS);
    for (String transport : StringUtil.split(TeamCityProperties.getProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS), true, ',')) {
      allowed.add(transport.trim().toLowerCase());
    }
    return allowed;
  }

  @NotNull
  public static String getAllowedTransportsHint() {
    return "Allowed: " + StringUtil.join(", ", getEffectiveAllowedTransports()) +
           ". To allow another, add it to the '" + Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS + "' internal property.";
  }

  private static boolean isAllowedTransport(@NotNull String url) {
    String name = getTransportName(url);
    if (name != null) {
      return getEffectiveAllowedTransports().contains(name);
    }
    return url.indexOf(':') < 0 && !url.startsWith("-");
  }

  /**
   * Returns the transport name a raw URL declares (scheme, remote-helper name, or {@code "ssh"} for
   * scp-like syntax), or {@code null} if the URL matches none of the recognized shapes.
   */
  @Nullable
  public static String getTransportName(@Nullable String rawUrl) {
    if (rawUrl == null) return null;
    String url = rawUrl.trim();

    Matcher m = SCHEME_PREFIX.matcher(url);
    if (m.find()) return m.group(1).toLowerCase();

    m = REMOTE_HELPER_PREFIX.matcher(url);
    if (m.find()) return m.group(1).toLowerCase();

    if (SCP_LIKE_URL.matcher(url).matches()) return "ssh";

    return null;
  }

  private static boolean isFileUrlAllowed() {
    return DevelopmentMode.isEnabled || TeamCityProperties.getBoolean(Constants.ALLOW_FILE_URL);
  }

  /**
   * Checks whether the provided raw Git remote URL suggests local file access.
   * The method is conservative and aims to avoid false positives for network URLs
   * such as ssh://... or scp-like user@host:path syntax.
   */
  public static boolean isLocalFileAccess(@Nullable String rawUrl) {
    return classify(rawUrl) != null;
  }

  /**
   * Classifies the provided raw Git remote URL into a local access reason, or returns null if it does not
   * look like local filesystem access.
   */
  @Nullable
  public static LocalReason classify(@Nullable String rawUrl) {
    if (rawUrl == null) return null;
    String url = rawUrl.trim();
    if (url.isEmpty()) return null;

    String lower = url.toLowerCase();

    // 1) file: scheme (file://, file:/, file:///C:/...)
    if (lower.startsWith("file:")) {
      return LocalReason.FILE_SCHEME;
    }

    // 2) Windows UNC path \\server\share\...
    if (url.startsWith("\\\\")) {
      return LocalReason.WINDOWS_UNC;
    }

    // 3) Windows drive path: C:\path or C:/path or even C:relative
    // Make sure we don't confuse scp-like syntax (user@host:path)
    if (looksLikeWindowsDrive(url)) {
      return LocalReason.WINDOWS_DRIVE;
    }

    // 4) Unix absolute path: /path/to/repo
    if (url.startsWith("/")) {
      return LocalReason.UNIX_ABSOLUTE;
    }

    // 5) Unix relative path: ./repo, ../repo, ~/repo
    if (url.startsWith("./") || url.startsWith("../") || url.startsWith("~/") || url.startsWith(".\\") || url.startsWith("..\\") || url.startsWith("~\\")) {
      return LocalReason.UNIX_RELATIVE;
    }

    // 6) If it contains a scheme (://), treat as network URL
    int schemeIdx = url.indexOf("://");
    if (schemeIdx >= 0) {
      return null;
    }

    // 7) scp-like syntax [user@]host:path should not be considered local
    int colonIdx = url.indexOf(':');
    if (colonIdx > 0) {
      return null; // likely scp syntax
    }

    // 8) Bare relative like "repo.git" or "path/to/repo" (without scheme/host) is generally local path
    // Avoid marking single word without path separators as local as it could be a host alias in some setups,
    // but Git would treat plain words as local paths too. We conservatively require a path separator to decide.
    if (url.contains("/") || url.contains("\\")) {
      return LocalReason.UNIX_RELATIVE;
    }

    return null;
  }

  private static boolean looksLikeWindowsDrive(@NotNull String url) {
    if (url.length() < 2) return false;

    char firstChar = url.charAt(0);
    char secondChar = url.charAt(1);

    return isAsciiLetter(firstChar) && secondChar == ':';
  }

  private static boolean isAsciiLetter(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
  }
}
