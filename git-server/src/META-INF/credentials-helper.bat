@echo off

if ""%1"" == ""erase"" goto erase

{JAVA} -cp {CREDENTIALS_CLASSPATH} {CREDENTIALS_CLASS} %*
goto end

:erase
del "{CREDENTIALS_SCRIPT}"

:end