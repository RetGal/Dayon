#!/bin/sh
ABS_PATH=$(dirname $(realpath "$0"))
if [ ! -f "$ABS_PATH/dayon.sh" ]; then
  "$ABS_PATH/dayon/dayon.sh" mpo.dayon.assisted.AssistedRunner "$1" "$2" "$3" "$4"
else
  "$ABS_PATH/dayon.sh" mpo.dayon.assisted.AssistedRunner "$1" "$2" "$3" "$4"
fi