#!/bin/sh
set -eu

VERSION=$(grep "<version>" ../pom.xml | head -1 | sed -E 's/.*<version>(.*)<\/version>.*/\1/')
grep "<release version=\"${VERSION}" ./dayon.appDir/usr/share/metainfo/dayon.appdata.xml > /dev/null
if [ $? -ne 0 ]; then
  echo "Expected release version ${VERSION} not found in 'dayon.appdata.xml'"
  exit 1
fi

case "$@" in
  *jlink*)
    JLINK=1
    ;;
  *)
    JLINK=0
    ;;
esac

cd ..
if [ ! -f "./dist/dayon.jar" ] || { [ $JLINK -eq 1 ] && [ ! -d "./target/jlink" ]; }; then
  if [ $JLINK -eq 0 ]; then
    echo "Building the jar"
    mvn clean package -DskipTests >/dev/null
  else
    echo "Building the jar and jre"
    mvn clean package -Pdefault,jlink -DskipTests >/dev/null
  fi
fi
if [ $? -ne 0 ]; then
  echo "Build failed."
  exit 1
fi
cd -

echo "Updating desktop entries"
sed -i "s/X-AppImage-Version=.*/X-AppImage-Version=${VERSION}/" dayon.appDir/*.desktop

echo "Cleaning appDir"
mkdir -p ./dayon.appDir/usr/lib/jre
rm -rf ./dayon.appDir/usr/lib/jre/*

echo "Copying files to appDir"
cp ../dist/dayon.jar ./dayon.appDir/usr/bin/
cp ../resources/dayon.png ./dayon.appDir/
cp -r ../resources/license ./dayon.appDir/usr/

if [ $JLINK -eq 0 ]; then
  echo "Copying JRE from resources"
  cp -r ../resources/jrex/* ./dayon.appDir/usr/lib/jre/
else
  echo "Copying JRE from jlink"
  cp -r ../target/jlink/jre/* ./dayon.appDir/usr/lib/jre/
fi

if [ ! -f "appimagetool" ]; then
  echo "Downloading appimagetool"
  curl -fLSs https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage -o appimagetool
  chmod +x appimagetool
fi
echo "Building AppImage"
./appimagetool -n ./dayon.appDir ./dayon-x86_64.AppImage -u "gh-releases-zsync|retgal|dayon|latest|dayon-x86_64.AppImage.zsync"
