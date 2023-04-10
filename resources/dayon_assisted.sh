#!/bin/sh
cross_realpath() {
  if ! realpath "${1}" 2>/dev/null; then
    [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
  fi
}
ABS_PATH=$(dirname "$(cross_realpath "$0")")
if [ ! -f "${ABS_PATH}/dayon.sh" ]; then
  ABS_PATH=${ABS_PATH}/dayon/dayon.sh
else
  ABS_PATH=${ABS_PATH}/dayon.sh
fi
"${ABS_PATH}" mpo.dayon.assisted.AssistedRunner "$@"