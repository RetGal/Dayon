#!/bin/sh
ABS_PATH=$(dirname $(realpath "$0"))
if [ ! -f "$ABS_PATH/dayon.sh" ]; then
  "$ABS_PATH/dayon/dayon.sh" mpo.dayon.assistant.AssistantRunner "$1"
else
  "$ABS_PATH/dayon.sh" mpo.dayon.assistant.AssistantRunner "$1"
fi