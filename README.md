# scio-back
### Issues
* Some artifacts are not published by upstream.
  * NLP Models that cannot be found:
    * en-ner-ta.bin (OpenNLP Threat Actor Model)
    * en-ner-vulnerability.bin (OpenNLP Vulnerability Model)
  * Artifacts that are provided but cannot be recreated or updated by users:
    * tlds-alpha-by-domain.txt
    * sectors.cfg
    * tools.cfg
### Configuration
#### Document Handler
There are multiple methods to ingest and handle new documents for processing.
These are defined by the handlers variable in scio-back/doc.
##### Choosing a document handler
You can either define a document handler through the .ini config, or by commandline argument.
There must be a section in the .ini config corresponding to the wanted storage location.

An example would be:
1) Run the application with the commandline argument ```--handler beanstalk```
2) In the .ini configuration have for example the following:
```
[handler-beanstalk]
host = localhost
port = 11300
queue = doc
temp-dir = /var/lib/scio
```

#### Storage
SCIO can store its results to a varied range of locations. The locations are defined 
in the code in the scio-back/storage namespace. A storage location can be anything from
AWS S3, NiFi, Elasticsearch, a folder on the filesystem, Redis, etc.

##### Choosing a storage location
You can either define a storage location through the .ini config, or by commandline argument.
There must be a section in the .ini config corresponding to the wanted storage location.

An example would be:
1) Run the application with the commandline argument ```--storage elasticsearch```
2) In the .ini configuration have for example the following:
```
[storage-elasticsearch]
schema = http
host = 127.0.0.1
port = 9200
index = scio
doc-type = doc
```

#### CLI Options
```
Options:
      --config-file FILE  /etc/scio.ini  Configuration file
      --handler HANDLER   beanstalk      Handler for new documents
      --storage STORAGE   elasticsearch  Storage location for analysis results
  -h, --help                             Displays this message
```

### Bootstrap
```
git clone https://github.com/mnemonic-no/act-scio.git
cd act-scio
cd etc
./download_data.sh
```

### Build
```
lein uberjar
```

## Test
```
lein test
```

## Usage
```
java -jar ./target/uberjar/scio-back-[VERSION]-standalone.jar
```

## License
Copyright © 2016-2019 by mnemonic AS <opensource@mnemonic.no>

Permission to use, copy, modify, and/or distribute this software for
any purpose with or without fee is hereby granted, provided that the
above copyright notice and this permission notice appear in all
copies.

THE SOFTWARE IS PROVIDED "AS IS" AND ISC DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL ISC BE LIABLE FOR ANY
SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT
