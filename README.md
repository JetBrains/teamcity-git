Git plugin for TeamCity
=======================

How to build .zip file
----------------------

1. Go to `Idea -> Properties -> Appearance & Behavior -> Path Variables`;
2. Fill variable `TeamCityDistribution` value as path to _TeamCity directory_;
3. Go to `Build -> Build Artifacts -> zip -> Build`;
4. After that plugin file `jetbrains.git.zip` will be at `dist` folder.

_TeamCity directory_ is:
* In case when you have sourcecode of TeamCity - it is path to `dist-teamcity-tomcat` artifact (`Build -> Build Artifacts -> dist-teamcity-tomcat -> Build`, then folder will be `<teamcity project dir>/.idea_artifacts/dist-teamcity-tomcat`);
* In case when you have TeamCity tar.gz file then it is path to unpacked distributive directory.
