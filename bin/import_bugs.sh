#!/bin/bash
### Re-populate the entire ES index from bugzilla.

### Setup ES indexes:
##curl -XDELETE 'http://elasticsearch7.metrics.scl3.mozilla.com:9200/bugs'
##curl -XPUT 'http://elasticsearch7.metrics.scl3.mozilla.com:9200/bugs' -d @/home/mark/mozilla/github/bugzilla_etl/configuration/es/bug_version.json

### Populate ES indexes:
BASE_DIR=/opt/pentaho/kettle/etl/bugzilla_etl
KETTLE_JOB=$BASE_DIR/jobs/run_full_update.kjb
cd $BASE_DIR/kettle/

# Be sure to set KETTLE_HOME appropriately before running this script.

for bug_id in $(seq 0 20000 820000); do
   echo "Processing batch ${bug_id}"
   time ./kitchen.sh -file $KETTLE_JOB -param:BUG_IDS_PARTITION="(bug_id >= ${bug_id} and bug_id < (${bug_id} + 20000))" > /var/log/etl/bugzilla_etl_import_${bug_id}.log
done
