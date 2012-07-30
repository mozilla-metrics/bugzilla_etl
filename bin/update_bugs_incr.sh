#!/bin/bash
### Update ES indexes with any new bugs since last run
### Be sure to set KETTLE_HOME appropriately before running this script.
BASE_DIR=/opt/pentaho/kettle/etl/bugzilla_etl
KETTLE_JOB=$BASE_DIR/jobs/run_incremental_update.kjb
cd $BASE_DIR/kettle/

# Last Run timestamp can be found in the "bugs_lastrun_file" var in kettle.properties
# As of 2012/07/05, currently set to /home/mreid/BZ_LAST_RUN
LOG_LEVEL=Basic
TS=$(date +%s000)
LOG=/var/log/etl/bugzilla_etl_incr.log
echo "*** Processing incremental updates.  Current OS Time is $TS" >> $LOG

./kitchen.sh -file $KETTLE_JOB -level $LOG_LEVEL >> $LOG
