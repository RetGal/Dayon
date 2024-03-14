#!/bin/sh
ASSISTED_DESKTOP=/usr/share/applications/DayonAssisted.desktop
if [ ! "$(whoami)" = "root" ]; then
  echo "Setting up desktop launchers for user '$(whoami)' only."
  echo "Run as super user in order to install globally."
  mkdir -p /home/"$(whoami)"/.local/share/applications
  ASSISTED_DESKTOP=/home/"$(whoami)"/.local/share/applications/DayonAssisted.desktop
elif [ ! -d "/usr/share/applications" ]; then
  echo "Fatal: Unknown environment - '/usr/share/applications' not found."
  exit 78
fi

if ! which java >/dev/null 2>&1; then
	echo "***************************************************************************************"
	echo "* Important: Fensterkitt Support App requires a Java Runtime Environment (JRE) to run.*"
	echo "* You will have to install a JRE afterwards - e.g. 'sudo apt-get install default-jre' *"
	echo "***************************************************************************************"
fi

INSTALL_DIR=$(dirname "$0")
if [ "$INSTALL_DIR" = "." ]; then
	INSTALL_DIR=$(pwd)
fi
chmod +x "${INSTALL_DIR}"/dayon*sh

cat <<EOF > "${ASSISTED_DESKTOP}"
[Desktop Entry]
Name=Dayon! Assisted
Version=1.0
Exec=${INSTALL_DIR}/dayon_assisted.sh
Comment=Request remote assistance
Comment[de]=Remotesupport erbitten
Comment[es]=Solicitar asistencia remota
Comment[fr]=Demander assistance à distance
Comment[it]=Richiedere assistenza remota
Comment[ru]=Запросить удаленную помощь
Comment[sv]=Begär distanshjälp
Comment[tr]=Uzaktan yardım isteyin
Comment[zh]=请求远程协助
Keywords=remote;support;get help
Icon=${INSTALL_DIR}/dayon.png
Type=Application
Terminal=false
StartupNotify=true
Categories=RemoteAccess;Network;
EOF

echo "Fensterkitt Support App setup finished successfully - happy sessions!"
