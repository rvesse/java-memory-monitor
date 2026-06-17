#!/usr/bin/env bash

PID=$1

function error() {
    local RET=$1
    shift
    echo $* >&2
    exit ${RET}
}

function blankLine() {
    echo ""
}

if [ -z "${PID}" ]; then
  error 1 "No PID to prepare memory map report available"
fi

if [ ! -f /proc/${PID}/maps ]; then
  error 2 "No /proc/${PID}/maps file available, this script only works on OSes with a /proc/ filesystem available"
fi

echo "PID ${PID}"
blankLine

rm -f /tmp/${PID}-mapped-files.txt >/dev/null 2>&1
cat /proc/${PID}/maps | grep -v "\[" | awk '{print $6}' | sed '/^[[:blank:]]*$/d' | \
    sort | uniq > /tmp/${PID}-mapped-files.txt
NON_FILE_MAPS=$(cat /proc/${PID}/maps | grep -v "/" | awk '{print $6}' | sort | uniq | wc -l | awk '{print $1}')

echo "Found $(wc -l /tmp/${PID}-mapped-files.txt | awk '{print $1}') memory mapped files"
echo "Found ${NON_FILE_MAPS} non-file memory maps"
blankLine

# Take a point in time copy of the virtual /proc/<pid>/smaps file so we can consistently analyse it
# without needing the OS to repeatedly regenerate it for us when it may change on each call
rm -f /tmp/${PID}-smaps.txt >/dev/null 2>&1
cat /proc/${PID}/smaps > /tmp/${PID}-smaps.txt

function mmapUsage() {
    local CATEGORY=${1:-Rss}
    cat /proc/${PID}/smaps | grep ${CATEGORY} | awk '{print $2}' | \
        grep -v 0 | awk '{s+=$1} END {print s}'
}

function smapUsage() {
  local LINE_NUM=$1
  local LINE_ADJ=$2
  sed "$((${LINE_NUM} + ${LINE_ADJ}))q;d" /tmp/${PID}-smaps.txt | awk '{print $2}'
}

TOTAL_RSS=$(mmapUsage)
TOTAL_REFERENCED=$(mmapUsage Referenced)
echo "Total Rss: ${TOTAL_RSS} KB"
echo "Total Referenced: ${TOTAL_REFERENCED}  KB"

# For each memory-mapped file sum up how much resident memory it's currently using
rm -f /tmp/${PID}-mapped-files-table.txt
LARGEST_FILE=
LARGEST_RSS=0
FILE_RSS=0
FILE_REFERENCED=0
while read -r FILE; do
  # Find all the lines in the smaps file that reference the file of interest
  RSS=0
  SEGMENTS=0
  while read -r LINE_NUM; do
    SEGMENTS=$(( ${SEGMENTS} + 1))
    # Read the Rss memory for that file, this will be the 4th line after the filename
    MAP_RSS=$(smapUsage ${LINE_NUM} 4)
    if [ -n "${MAP_RSS}" ]; then
      RSS=$(( ${RSS} + ${MAP_RSS}))
      FILE_RSS=$(( ${FILE_RSS} + ${MAP_RSS}))
    fi

    # Read the Referenced memory for that file, this will be the 11th line after the filename
    MAP_REF=$(smapUsage ${LINE_NUM} 11)
    if [ -n "${MAP_REF}" ]; then
      FILE_REFERENCED=$(( ${FILE_REFERENCED} + ${MAP_REF}))
    fi

  done < <(grep -n "${FILE}" /tmp/${PID}-smaps.txt | cut -d ':' -f 1)
  echo "${FILE}" "${SEGMENTS}" "${RSS}" "KB" >> /tmp/${PID}-mapped-files-table.txt

  if [ "${RSS}" -gt "${LARGEST_RSS}" ]; then
    LARGEST_FILE=${FILE}
    LARGEST_RSS=${RSS}
  fi
done < /tmp/${PID}-mapped-files.txt

echo "File Rss: ${FILE_RSS} KB"
echo "File Referenced: ${FILE_REFERENCED} KB"
echo "Non-File Rss: $(( ${TOTAL_RSS} - ${FILE_RSS})) KB"
echo "Non-File Referenced: $(( ${TOTAL_REFERENCED} - ${FILE_REFERENCED})) KB"

blankLine
echo "File" "#Maps" "Rss"
cat /tmp/${PID}-mapped-files-table.txt | sort -rg -k 3,3 -k 2,2
blankLine

if [ -n "${LARGEST_FILE}" ]; then
  echo "Largest Memory Mapped File: ${LARGEST_FILE} ${LARGEST_RSS} KB"
  blankLine
fi

# Clean up temporary files
rm -f /tmp/${PID}-mapped-files-table.txt >/dev/null 2>&1
rm -f /tmp/${PID}-mapped-files.txt >/dev/null 2>&1
rm -f /tmp/${PID}-smaps.txt >/dev/null 2>&1

exit 0
