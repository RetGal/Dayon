#!/bin/sh
DAYON_HOME=$(dirname $(realpath "$0"))
CLASSPATH="$DAYON_HOME/dayon.jar"
/app/jre/bin/java -Ddayon.log=file -cp "${CLASSPATH}" "$1" "$2" "$3" "$4"