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

echo "Found $(wc -l /tmp/${PID}-mapped-files.txt | awk '{print $1}') memory mapped files"
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

echo "Rss: $(mmapUsage) KB"
echo "Referenced: $(mmapUsage Referenced) KB"
blankLine

# For each memory-mapped file sum up how much resident memory it's currently using
echo "File" "#Maps" "Rss"
LARGEST_FILE=
LARGEST_RSS=0
while read -r FILE; do
  # Find all the lines in the smaps file that reference the file of interest
  RSS=0
  SEGMENTS=0
  while read -r LINE_NUM; do
    SEGMENTS=$(( ${SEGMENTS} + 1))
    # Read the Rss memory for that file, this will be the 4th line after the filename
    MAP_RSS=$(sed "$((${LINE_NUM} + 4))q;d" /tmp/${PID}-smaps.txt | awk '{print $2}')
    if [ -n "${MAP_RSS}" ]; then
      RSS=$(( ${RSS} + ${MAP_RSS}))
    fi
  done < <(grep -n "${FILE}" /tmp/${PID}-smaps.txt | cut -d ':' -f 1)
  echo "${FILE}" "${SEGMENTS}" "${RSS}" "KB"

  if [ "${RSS}" -gt "${LARGEST_RSS}" ]; then
    LARGEST_FILE=${FILE}
    LARGEST_RSS=${RSS}
  fi
done < /tmp/${PID}-mapped-files.txt
blankLine
if [ -n "${LARGEST_FILE}" ]; then
  echo "Largest Memory Mapped File: ${LARGEST_FILE} ${LARGEST_RSS} KB"
  blankLine
fi

# Clean up temporary files
rm -f /tmp/${PID}-mapped-files.txt >/dev/null 2>&1
rm -f /tmp/${PID}-smaps.txt >/dev/null 2>&1

exit 0
