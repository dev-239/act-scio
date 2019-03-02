#!/usr/bin/env bash

set -e

BASE="./data"
LOG_DIR="${BASE}/log"
SCIO_DIR="../tools"
DOWNLOAD_DIR="${BASE}/download"
DB_DIR="${BASE}/db"
SUBMIT_DIR="${BASE}/submit"
FILES_DIR="${BASE}/files"
SCHEMA_SQL="schema.sql"
TYPES="pdf
doc
xls
csv
xml"
DB="${DB_DIR}/feeds.db"
NOW=`date +%s`
NEW_FEEDS_LOG="feeds.log"
NEW_FILES_LOG="files.log"
HISTORICAL_SUBMIT_DIR="${SUBMIT_DIR}/historical"
NOW_DIR="${HISTORICAL_SUBMIT_DIR}/${NOW}"
LATEST_DIR="${SUBMIT_DIR}/latest"

function log() {
    TS=`date +'%Y-%m-%d %H:%M:%S'`
    echo -e "[$TS]\t${1}"
}

function create_dir {
    if [[ ! -d "$1" ]]; then
        mkdir "$1"
    fi
}

function copy_to_latest() {
    if [[ -f "${1}/${3}" ]]; then
        cp -f "${1}/${3}" "${2}/${3}"
    else
        log "no new ${3} was found"
    fi
}

log "Creating directories"
create_dir ${BASE}
create_dir ${LOG_DIR}
create_dir ${DOWNLOAD_DIR}
create_dir ${DB_DIR}
create_dir ${SUBMIT_DIR}
create_dir ${HISTORICAL_SUBMIT_DIR}
create_dir ${FILES_DIR}
create_dir ${NOW_DIR}
create_dir ${LATEST_DIR}
for TYPE in ${TYPES}; do create_dir ${FILES_DIR}/${TYPE}; done

TYPE_ARGS=""
for TYPE in ${TYPES}; do
    TYPE_ARGS+="--download_${TYPE} --${TYPE}_store ${FILES_DIR}/${TYPE} "
done

log "Downloading feeds"
python3 feed_download.py ${TYPE_ARGS} \
--log ${BASE}/log/feed_download.log \
--output ${BASE}/download --meta ${BASE}/download \
--feeds feeds.txt \
--verbose

log "Create DB to track feeds and files"
sqlite3 ${DB} < ${SCHEMA_SQL}

log "Collect new feeds that can be ingested"
python3 collect_new_feeds.py --log ${BASE}/log/upload.log --cache ${DB} -o "${NOW_DIR}/${NEW_FEEDS_LOG}" ${BASE}/download
copy_to_latest ${NOW_DIR} ${LATEST_DIR} ${NEW_FEEDS_LOG}

log "Collect new files that can be ingested"
for TYPE in ${TYPES}; do
	python3 collect_new_files.py -c ${DB} -a ${FILES_DIR}/${TYPE} -o "${NOW_DIR}/${NEW_FILES_LOG}"
done
copy_to_latest ${NOW_DIR} ${LATEST_DIR} ${NEW_FILES_LOG}
