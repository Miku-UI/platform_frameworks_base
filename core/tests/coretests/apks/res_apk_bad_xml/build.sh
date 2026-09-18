#!/bin/bash

set -e

# aapt2 can't take an invalid xml and put it inside an apk, so we need to start with something
# valid and replace it afterwards

aapt2 compile --dir res-src -o compiled.flata
aapt2 link \
    --manifest AndroidManifest.xml \
    -o ResApkBadXml.apk \
    compiled.flata
rm compiled.flata

# now update the huge.xml file in the apk with a 2GB version that should break everything
unzip huge.xml.zip
zip -u ResApkBadXml.apk res/xml/huge.xml
rm -rf res/