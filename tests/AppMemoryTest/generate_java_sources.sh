#!/bin/bash

if [ "$#" -ne 4 ]; then
  echo "Usage: $0 <num_classes> <num_methods> <temp_dir> <output_file>"
  exit 1
fi

NUM_CLASSES=$1
NUM_METHODS=$2
TEMP_DIR=$3
OUTPUT_FILE=$4
PACKAGE_NAME="android.app.memory.tests.generated"
BASE_DIR=$TEMP_DIR/android/app/memory/tests/generated

mkdir -p $BASE_DIR

for i in $(seq 1 $NUM_CLASSES); do
  CLASS_NAME="GeneratedClass${i}"
  FILE_PATH="$BASE_DIR/${CLASS_NAME}.java"
  echo "package ${PACKAGE_NAME};" > $FILE_PATH
  echo "public class ${CLASS_NAME} {" >> $FILE_PATH
  for j in $(seq 1 $NUM_METHODS); do
    echo "    public int generatedMethod${j}(int x) {" >> $FILE_PATH
    echo "        return x + ${i} * ${j};" >> $FILE_PATH
    echo "    }" >> $FILE_PATH
  done
  echo "}" >> $FILE_PATH
done

(cd $TEMP_DIR && zip -r temp.zip .)
mv $TEMP_DIR/temp.zip $OUTPUT_FILE
