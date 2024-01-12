

package jetbrains.buildServer.buildTriggers.vcs.git;

/** Git change type */
public enum ChangeType {
  /** the file is added */
  ADDED,
  /** the file is deleted */
  DELETED,
  /** the file content (or content+mode) changed */
  MODIFIED,
  /** the file mode only changed */
  FILE_MODE_CHANGED,
  /** no change detected */
  UNCHANGED,
}