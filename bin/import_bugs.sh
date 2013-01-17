#!/bin/bash
### Re-populate the entire ES index from bugzilla.

IMPORT_DAY=$(date +%Y%m%d)
### Setup ES indexes:
# In the example below, the data was last imported on 20120919, and today (IMPORT_DAY) is 20130515.  Replace these with the real values.
# ---------------------
# 1. Create the new index:
#     curl -XPUT 'http://elasticsearch7.metrics.scl3.mozilla.com:9200/bugs20130515' -d @/opt/pentaho/kettle/etl/bugzilla_etl/configuration/es/bug_version.json
# 2. Create a new config file, setting ES_INDEX to "bugs20130515"
#     mkdir -p /opt/pentaho/kettle/etl/bugzilla_etl/repull20130515/.kettle
#     cp /opt/pentaho/kettle/.kettle/kettle.properties /opt/pentaho/kettle/etl/bugzilla_etl/repull20130515/.kettle/
#     <edit /opt/pentaho/kettle/etl/bugzilla_etl/repull20130515/.kettle/kettle.properties to add>
#       ES_INDEX=bugs20130515
# 3. Copy the last update file
#     cp /opt/pentaho/kettle/etl/bugzilla_etl/BZ_LAST_RUN /opt/pentaho/kettle/etl/bugzilla_etl/BZ_LAST_RUN.20130515
# 4. Run this script to import all the data to the new index (ETA: 2 to 3 hours)
#     time /opt/pentaho/kettle/etl/bugzilla_etl/bin/import_bugs.sh
# 5. Update the "bugs" ES Alias to remove the old index and add the new one:
#     curl -XPOST   'http://elasticsearch7.metrics.scl3.mozilla.com:9200/_aliases' -d '{ "actions" : [ { "remove" : { "index" : "bugs20120919", "alias" : "bugs" } }, { "add"    : { "index" : "bugs20130515", "alias" : "bugs" } } ] }'
# 6. Rewind the incremental updates to make sure we didn't miss anything:
#     mv /opt/pentaho/kettle/etl/bugzilla_etl/BZ_LAST_RUN.20130515 /opt/pentaho/kettle/etl/bugzilla_etl/BZ_LAST_RUN
# 7. Delete the old index:
#     curl -XDELETE 'http://elasticsearch7.metrics.scl3.mozilla.com:9200/bugs20120919'

### Populate ES indexes:
BASE_DIR=/opt/pentaho/kettle/etl/bugzilla_etl
KETTLE_JOB=$BASE_DIR/jobs/run_full_update.kjb
cd $BASE_DIR/kettle/

# Be sure to set KETTLE_HOME appropriately before running this script.
# So in $KETTLE_HOME/.kettle/kettle.properties, you would add:
#  ES_INDEX=bugs20120919
export KETTLE_HOME=/opt/pentaho/kettle/etl/bugzilla_etl/repull$IMPORT_DAY/

INCREMENT=10000
for bug_id in $(seq 0 $INCREMENT 850000); do
   echo "Processing batch ${bug_id}"
   time ./kitchen.sh -file $KETTLE_JOB -param:BUG_IDS_PARTITION="(bug_id >= ${bug_id} and bug_id < (${bug_id} + $INCREMENT))" > /var/log/etl/bugzilla_etl_import_${bug_id}.log
done
