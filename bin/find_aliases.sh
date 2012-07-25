#!/bin/bash
### SETUP ES INDEXES:
##curl -XDELETE 'http://elasticsearch7.metrics.scl3.mozilla.com:9200/bugs'
##curl -XPUT 'http://elasticsearch7.metrics.scl3.mozilla.com:9200/bugs' -d @/home/mark/mozilla/github/bugzilla_etl/configuration/es/bug_version.json

### POPULATE ES INDEXES:
KETTLE_JOB=~/mozilla/github/bugzilla_etl/transformations/find_aliases.ktr
cd ~/mozilla/kettle/data-integration4.3/

PARTITION="$1"
if [ -z "$PARTITION" ]; then
   PARTITION="(bug_id = 718067)"
fi

time ./pan.sh -level Minimal -file $KETTLE_JOB -param:BUG_IDS_PARTITION="$PARTITION"
