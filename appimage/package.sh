#!/bin/sh
VERSION=$(grep "<version>" ../pom.xml | head -1 | sed -E 's/.*<version>(.*)<\/version>.*/\1/')
grep "<release version=\"${VERSION}" ./dayon.appDir/usr/share/metainfo/dayon.appdata.xml > /dev/null
if [ $? -ne 0 ]; then
  echo "Expected release version ${VERSION} not found in 'dayon.appdata.xml'"
  exit 1
fi

set -e

if [ ! -f "../dist/dayon.jar" ]; then
  echo "Building the application jar first."
  cd ..
  mvn clean package -DskipTests
  if [ $? -ne 0 ]; then
    echo "Build failed."
    exit 1
  fi
  cd -
fi

echo "Cleaning appDir"
mkdir -p ./dayon.appDir/usr/lib
rm -rf ./dayon.appDir/usr/lib/jrex
echo "Copying files to appDir"
cp ../dist/dayon.jar ./dayon.appDir/usr/bin/
cp ../resources/dayon.png ./dayon.appDir/
cp -r ../resources/jrex ./dayon.appDir/usr/lib/
cp -r ../resources/license ./dayon.appDir/usr/

if [ ! -f "appimagetool" ]; then
  echo "Downloading appimagetool"
  curl -fLSs https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage -o appimagetool
  chmod +x appimagetool
fi
echo "Building AppImage"
./appimagetool -n ./dayon.appDir ./dayon-x86_64.AppImage
