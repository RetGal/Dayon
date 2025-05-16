#!/bin/sh
cross_realpath() {
  if ! realpath "${1}" 2>/dev/null; then
    [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
  fi
}
HERE=$(dirname "$(cross_realpath "$0")")
JAVA=${HERE}/../lib/jrex/bin/java
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
JAR="${HERE}/dayon.jar"
${JAVA} ${JAVA_OPTS} ${LOG} -jar "${JAR}" "$@"
