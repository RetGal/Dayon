#!/bin/sh
DAYON_HOME=$(dirname $(realpath "$0"))
JAVA_HOME=`ls -l /etc/alternatives/java | awk -F'> ' '{print $2}' | awk -F'/bin/java' '{print $1}'`
# Your favorite JRE/JDK 1.8+ ...
#JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64/jre"
JAVA=${JAVA_HOME}/bin/java
JAVA_OPTS=""
CLASSPATH="$DAYON_HOME/dayon.jar"

${JAVA} ${JAVA_OPTS} -Ddayon.log=file -cp "${CLASSPATH}" "$1" "$2" "$3" "$4"