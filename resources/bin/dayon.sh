#!/bin/sh
DAYON_HOME=$(dirname "$0")/..
# Your favorite JRE/JDK 1.6+ ...
#JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64/jre"
JAVA_HOME=`ls -l /etc/alternatives/java | awk -F'> ' '{print $2}' | awk -F'/bin/java' '{print $1}'`
JAVA=$JAVA_HOME/bin/java
JAVA_OPTS="-Xms64M -Xmx256M"
CLASSPATH="$DAYON_HOME/lib/dayon.jar:$DAYON_HOME/lib/jetty-http-9.4.17.v20190418.jar:$DAYON_HOME/lib/jetty-io-9.4.17.v20190418.jar:$DAYON_HOME/lib/jetty-server-9.4.17.v20190418.jar:$DAYON_HOME/lib/jetty-util-9.4.17.v20190418.jar:$DAYON_HOME/lib/javax.servlet-api-3.1.0.jar:$DAYON_HOME/lib/bzip2-0.9.1.jar:$DAYON_HOME/lib/grizzly-lzma-1.9.65.jar"

$JAVA $JAVA_OPTS -Ddayon.log=file -cp $CLASSPATH $1 $2