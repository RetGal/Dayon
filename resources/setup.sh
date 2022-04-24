#!/bin/sh
if [ ! "$(whoami)" = "root" ]; then
	echo "This script must be run as super user - e.g. 'sudo sh setup.sh'"
	exit 77
fi

SHORTCUT_DIR=/usr/share/applications
if [ ! -d "$SHORTCUT_DIR" ]; then
	echo "Fatal: Unknown environment - '/usr/share/applications' not found."
	exit 78
fi

which java >/dev/null
if [ ! $? -eq 0 ]; then
	echo "***************************************************************************************"
	echo "* Important: Dayon! requires a Java Runtime Environment (JRE) to run.                 *"
	echo "* You will have to install a JRE afterwards - e.g. 'sudo apt-get install default-jre' *"
	echo "***************************************************************************************"
fi

INSTALL_DIR=$(dirname "$0")
if [ "$INSTALL_DIR" = "." ]; then
	INSTALL_DIR=$(pwd)
fi
chmod +x "${INSTALL_DIR}"/dayon*sh

cat <<EOF > /usr/share/applications/DayonAssistant.desktop
[Desktop Entry]
Name=Dayon! Assistant
Version=11.0
Exec=${INSTALL_DIR}/dayon_assistant.sh
Comment=Offer remote assistance
Comment[de]=Remotesupport anbieten
Comment[es]=Ofrecer asistencia remota
Comment[fr]=Offrir assistance à distance
Comment[it]=Offri assistenza remota
Comment[ru]=Предлагайте удаленную помощь
Comment[tr]=Uzaktan yardım sunun
Comment[zh]=提供远程协助
Keywords=remote;support;offer help
Icon=${INSTALL_DIR}/dayon.png
Type=Application
Terminal=false
StartupNotify=true
Encoding=UTF-8
Categories=RemoteAccess;Network;
EOF

cat <<EOF > /usr/share/applications/DayonAssisted.desktop
[Desktop Entry]
Name=Dayon! Assisted
Version=11.0
Exec=${INSTALL_DIR}/dayon_assisted.sh
Comment=Request remote assistance
Comment[de]=Remotesupport erbitten
Comment[es]=Solicitar asistencia remota
Comment[fr]=Demander assistance à distance
Comment[it]=Richiedi assistenza remota
Comment[ru]=Запросить удаленную помощь
Comment[tr]=Uzaktan yardım isteyin
Comment[zh]=请求远程协助
Keywords=remote;support;get help
Icon=${INSTALL_DIR}/dayon.png
Type=Application
Terminal=false
StartupNotify=true
Encoding=UTF-8
Categories=RemoteAccess;Network;
EOF

echo "Dayon! setup finished successfully - happy sessions!"
