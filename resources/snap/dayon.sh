#!/bin/sh
CLASSPATH="bin/dayon.jar"
bin/java -Ddayon.log=file -cp ${CLASSPATH} $1 $2 $3 $4