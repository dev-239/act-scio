#!/usr/bin/env bash

set -e

BASE="./data"
LOG_DIR="${BASE}/log"
SCIO_DIR="../tools"
DOWNLOAD_DIR="${BASE}/download"
TYPES="pdf
doc
xls
csv
xml"
UPLOAD_DB="${BASE}/upload.db"
UPLOAD_SQL="upload.sql"
SUBMIT_DB="${BASE}/submitcache.db"
SUBMIT_SQL="submitcache.sql"

function create_dir {
    if [[ ! -d "$1" ]]; then
        mkdir "$1"
    fi
}

create_dir ${BASE}
create_dir ${LOG_DIR}
create_dir ${DOWNLOAD_DIR}
for TYPE in ${TYPES}; do create_dir ${BASE}/${TYPE}; done

TYPE_ARGS=""
for TYPE in ${TYPES}; do
    ARGS+="--download_${TYPE} --${TYPE}_store ${BASE}/${TYPE} "
done

python3 feed_download.py ${TYPE_ARGS} \
--log ${BASE}/log/feed_download.log \
--output ${BASE}/download --meta ${BASE}/download \
--feeds feeds.txt \
--verbose

sqlite3 ${UPLOAD_DB} < ${UPLOAD_SQL}
python3 upload.py --log ${BASE}/log/upload.log --cache ${BASE}/upload.db --debug ${BASE}/download

sqlite3 ${SUBMIT_DB} < ${SUBMIT_SQL}
for TYPE in ${TYPES}; do
	for FILENAME in `python3 submitcache.py -c ${SUBMIT_DB} -a ${BASE}/${TYPE}`; do
		${SCIO_DIR}/submit.py ${FILENAME}
	done
done
