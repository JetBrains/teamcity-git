#!/bin/sh

if [ "$1" = "erase" ]; then
  rm '{CREDENTIALS_SCRIPT}';
  exit;
fi

java -cp '{CREDENTIALS_CLASSPATH}' {CREDENTIALS_CLASS} $*