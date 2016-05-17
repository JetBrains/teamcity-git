@echo off

if ""%1"" == ""erase"" goto erase

java -cp {CREDENTIALS_CLASSPATH} {CREDENTIALS_CLASS} $%
goto end

:erase
del "{CREDENTIALS_SCRIPT}"

:end