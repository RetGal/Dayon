#!/bin/sh
CLASSPATH="bin/dayon.jar"
java -Ddayon.log=file -cp ${CLASSPATH} $1 $2 $3 $4