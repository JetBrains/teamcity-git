[![official JetBrains project](https://jb.gg/badges/official-flat-square.svg)](https://github.com/jetbrains#jetbrains-on-github)

Git plugin for TeamCity
=======================

How to build .zip file
----------------------
```
mvn package
```
The plugin zip(`jetbrains.git.zip`) will be located in the `target` folder

Running tests
--------------
 Locally: 
 ```
 setsid mvn -Pwith_tests test
 ```
 On server(without terminal)
 ```
 mvn -Pwith_tests test
 ```