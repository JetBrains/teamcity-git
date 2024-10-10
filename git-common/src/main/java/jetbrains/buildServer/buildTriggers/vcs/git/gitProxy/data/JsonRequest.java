package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data;

import com.google.gson.annotations.SerializedName;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class JsonRequest {
  public String auth;
  @SerializedName("class")
  public String _class;
  public String method;
  public Map<String, Object> args;

  public JsonRequest(@NotNull String auth, @NotNull String _class, @NotNull String method, @NotNull Map<String, Object> args) {
    this.auth = auth;
    this._class = _class;
    this.method = method;
    this.args = args;
  }
}
