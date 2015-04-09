/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.AskPassGenerator;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.EscapeEchoArgumentUnix;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.EscapeEchoArgumentWin;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.UnixAskPassGen;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl.WinAskPassGen;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.assertEquals;

@Test
public class AskPassGeneratorTest {

  private TempFiles myTempFiles;

  @BeforeMethod
  public void setUp() throws Exception {
    new TeamCityProperties() {{ setModel(new BasePropertiesModel() {});}};
    myTempFiles = new TempFiles();
  }

  @AfterMethod
  public void tearDown() throws Exception {
    myTempFiles.cleanup();
  }


  @DataProvider(name = "passwords")
  public static Object[][] passwords() {
    return new Object[][] {
      new Object[] {"a$b"},
      new Object[] {"pa$$word"},
      new Object[] {"p\\a$$word"},
      new Object[] {"p\\b$$word"},
      new Object[] {"p\\c$$word"},
      new Object[] {"p\\f$$word"},
      new Object[] {"p\\n$$word"},
      new Object[] {"p\\r$$word"},
      new Object[] {"p\\t$$word"},
      new Object[] {"p\\v$$word"},
      new Object[] {"p\\56$$word"},
      new Object[] {"p%ssw%%d"},
    };
  }


  @TestFor(issues = "TW-40688")
  @Test(dataProvider = "passwords")
  public void check_escaping(@NotNull String password) throws Exception {
    VcsRoot root = createRootWithPassword(password);
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setExePath(createGenerator().generate(new AuthSettings(root)).getCanonicalPath());
    ExecResult result = SimpleCommandLineProcessRunner.runCommand(cmd, null);
    assertEquals(password, result.getStdout());
  }


  private VcsRoot createRootWithPassword(@NotNull String password) {
    return vcsRoot().withFetchUrl("http://some.org/repo.git")
        .withAuthMethod(AuthenticationMethod.PASSWORD)
        .withUsername("name")
        .withPassword(password)
        .build();
  }


  @NotNull
  private AskPassGenerator createGenerator() throws IOException {
    return SystemInfo.isUnix ? new UnixAskPassGen(myTempFiles.createTempDir(), new EscapeEchoArgumentUnix())
                             : new WinAskPassGen(myTempFiles.createTempDir(), new EscapeEchoArgumentWin());

  }
}
