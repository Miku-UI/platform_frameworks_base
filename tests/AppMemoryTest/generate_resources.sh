#!/bin/bash

if [ "$#" -ne 4 ]; then
  echo "Usage: $0 <num_files> <num_strings_per_file> <temp_dir> <output_file>"
  exit 1
fi

NUM_FILES=$1
NUM_STRINGS=$2
TEMP_DIR=$3
OUTPUT_FILE=$4

VALUES_DIR=$TEMP_DIR/res/values
mkdir -p $VALUES_DIR

for i in $(seq 1 $NUM_FILES); do
  FILE_PATH="$VALUES_DIR/generated_strings_${i}.xml"
  echo '<?xml version="1.0" encoding="utf-8"?>' > $FILE_PATH
  echo '<resources>' >> $FILE_PATH
  for j in $(seq 1 $NUM_STRINGS); do
    STRING_NAME="generated_string_${i}_${j}"
    echo "    <string name=\"${STRING_NAME}\">This is a generated string resource to increase APK size. String ${i} ${j}</string>" >> $FILE_PATH
  done
  echo '</resources>' >> $FILE_PATH
done

(cd $TEMP_DIR && zip -r temp.zip .)
mv $TEMP_DIR/temp.zip $OUTPUT_FILE
