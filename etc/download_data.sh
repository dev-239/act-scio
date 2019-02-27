#!/usr/bin/env sh

DATA_DIR="data"
DOWNLOADS_DIR="downloads"
BACKUP_DIR="backup"

GEONAMES_URL="http://download.geonames.org/export/dump/"
CITIES_1000="cities1000"
CITIES_15000="cities15000"
COUNTRY_INFO="countryInfo.txt"

ISO_3166_CODES_URL="https://raw.githubusercontent.com/lukes/ISO-3166-Countries-with-Regional-Codes/master/all/all.json"
ISO_3166_CODES_FILE="ISO-3166-countries-with-regional-codes.json"

NLP_MODELS_URL="http://opennlp.sourceforge.net/models-1.5"
NLP_MODELS_LIST="wanted-models.txt"

NOW=`date +%s`

function backup() {
    if [[ -f "$1" ]]; then
        mv $1 "$BACKUP_DIR/$1.bak.$NOW"
    fi
}

function create_dir {
    if [[ ! -d "$1" ]]; then
        mkdir "$1"
    fi
}

create_dir ${DATA_DIR}
create_dir ${DOWNLOADS_DIR}
create_dir ${BACKUP_DIR}


cat ${NLP_MODELS_LIST} | while read MODEL; do
    if [[ -f "$DATA_DIR/$MODEL" ]]; then
        mv "$DATA_DIR/$MODEL" "$BACKUP_DIR/$MODEL"
    fi

    curl --silent "$NLP_MODELS_URL/$MODEL" -o "$DOWNLOADS_DIR/$MODEL"
    cp "$DOWNLOADS_DIR/$MODEL" "$DATA_DIR/$MODEL"
done

backup "$DATA_DIR/$CITIES_1000.txt"
curl --silent "$GEONAMES_URL/$CITIES_1000.zip" -o "$DOWNLOADS_DIR/$CITIES_1000.zip"
unzip -q "$DOWNLOADS_DIR/$CITIES_1000.zip" -d ${DATA_DIR}

backup "$DATA_DIR/$CITIES_15000.txt"
curl --silent "$GEONAMES_URL/$CITIES_15000.zip" -o "$DOWNLOADS_DIR/$CITIES_15000.zip"
unzip -q "$DOWNLOADS_DIR/$CITIES_15000.zip" -d ${DATA_DIR}

backup "$DATA_DIR/$COUNTRY_INFO"
curl --silent "$GEONAMES_URL/$COUNTRY_INFO" -o "$DOWNLOADS_DIR/$COUNTRY_INFO"
sed '/^#/d' "$DOWNLOADS_DIR/$COUNTRY_INFO" > "$DATA_DIR/$COUNTRY_INFO"

backup "$DATA_DIR/$ISO_3166_CODES_FILE"
curl --silent "$ISO_3166_CODES_URL" -o "$DOWNLOADS_DIR/$ISO_3166_CODES_FILE"
cp "$DOWNLOADS_DIR/$ISO_3166_CODES_FILE" "$DATA_DIR/$ISO_3166_CODES_FILE"

rm -rf ${DOWNLOADS_DIR}