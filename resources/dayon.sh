#!/bin/sh
DAYON_HOME=$(dirname $(realpath "$0"))
if which java >/dev/null; then
    JAVA=$(which java)
  else
    JAVA_HOME=`ls -l /etc/alternatives/java | awk -F'> ' '{print $2}' | awk -F'/bin/java' '{print $1}'`
    #JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64/jre"
    JAVA=${JAVA_HOME}/bin/java
fi
JAVA_OPTS=""
CLASSPATH="$DAYON_HOME/dayon.jar"

${JAVA} ${JAVA_OPTS} -Ddayon.log=file -cp "${CLASSPATH}" "$1" "$2" "$3" "$4"