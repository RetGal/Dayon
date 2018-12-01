#!/bin/sh

DAYON_HOME=$(dirname "$0")/..
# Your favorite JRE/JDK 1.6+ ...
#JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64/jre"
JAVA_HOME=`ls -l /etc/alternatives/java | awk -F'> ' '{print $2}' | awk -F'/bin/java' '{print $1}'`
JAVA=$JAVA_HOME/bin/java
JAVA_OPTS="-Xms32M -Xmx192M"

CLASSPATH="$DAYON_HOME/lib/dayon.jar:$DAYON_HOME/lib/jetty-http-9.4.14.v20181114.jar:$DAYON_HOME/lib/jetty-io-9.4.14.v20181114.jar:$DAYON_HOME/lib/jetty-server-9.4.14.v20181114.jar:$DAYON_HOME/lib/jetty-util-9.4.14.v20181114.jar:$DAYON_HOME/lib/javax.servlet-api-3.1.0.jar"

$JAVA $JAVA_OPTS -cp $CLASSPATH -Ddayon.log=file $2

