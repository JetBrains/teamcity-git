package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;

public interface RepackCommandServer {

  void repack(String path) throws VcsException;

}
