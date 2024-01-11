#!/bin/sh

if [ "$1" = "erase" ]; then
  rm '{CREDENTIALS_SCRIPT}';
  exit;
fi

{JAVA} -cp '{CREDENTIALS_CLASSPATH}' {CREDENTIALS_CLASS} $*