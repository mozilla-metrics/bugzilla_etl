#!/bin/bash
### Update ES indexes with any new bugs since last run
### Be sure to set KETTLE_HOME appropriately before running this script.
BASE_DIR=/opt/pentaho/kettle/etl/bugzilla_etl
KETTLE_JOB=$BASE_DIR/jobs/run_incremental_update.kjb
cd $BASE_DIR/kettle/

# Last Run timestamp can be found in the "bugs_lastrun_file" var in kettle.properties
# As of 2012/08/01, currently set to /opt/pentaho/kettle/etl/bugzilla_etl/BZ_LAST_RUN
LOG_LEVEL=Basic
TS=$(date +%s000)

# Use a temp log just for this run
LOG=/var/log/etl/bugzilla_etl_incr.$TS.log
echo "*** Processing incremental updates.  Current OS Time is $TS / $(date)" >> $LOG

# Kill previous processes
RUNNING_PROCESSES=`pgrep -f etljob=run_incremental_update.kjb`
if [ ! -z "$RUNNING_PROCESSES" ]; then
   echo "Killing the following stalled processes: $RUNNING_PROCESSES" | tee -a $LOG
   pkill -9 -f etljob=run_incremental_update.kjb
fi

# define a marker so that we can find this job using ps
export PENTAHO_DI_JAVA_OPTIONS="-Xmx1g -Detljob=run_incremental_update.kjb"

./kitchen.sh -file $KETTLE_JOB -level $LOG_LEVEL 2>&1 >> $LOG
if [ $? != 0 ]; then
   # grep -i exception $LOG
   cat $LOG
fi


# Append the temp log to the full log
cat $LOG >> /var/log/etl/bugzilla_etl_incr.log

# Clean up
rm $LOG
