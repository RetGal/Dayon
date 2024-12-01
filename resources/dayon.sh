#!/bin/sh
cross_realpath() {
  if ! realpath "${1}" 2>/dev/null; then
    [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
  fi
}
DAYON_HOME=$(dirname "$(cross_realpath "$0")")
if which java >/dev/null 2>&1; then
  JAVA=$(which java)
elif [ ! -f /etc/alternatives/java ]; then
  if [ ! -d /usr/libexec/java_home ]; then
    if [ -f /app/jre/bin/java ]; then
      JAVA=/app/jre/bin/java
    else
      JAVA=$(cross_realpath "jrex/bin/java")
    fi
  else
    JAVA=/usr/libexec/java_home/bin/java
  fi
else
  JAVA=$(ls -l /etc/alternatives/java | awk -F'> ' '{print $2}')
fi
case "$@" in
  *log=console*)
    LOG=
    ;;
  *)
    LOG="-Ddayon.log=file"
    ;;
esac
case "$@" in
  *assistant*)
    JAVA_OPTS="-Xmx512m"
    ;;
  *)
    JAVA_OPTS="-Xmx1g"
    ;;
esac
JAR="${DAYON_HOME}/dayon.jar"
${JAVA} ${JAVA_OPTS} ${LOG} -jar "${JAR}" "$@"
