#!/bin/bash

if [ -z "$KETTLE_BASE" ]; then
   KETTLE_BASE=/opt/pentaho/kettle/etl/bugzilla_etl
fi

KETTLE_JOB=$KETTLE_BASE/jobs/run_full_update.kjb
cd $KETTLE_BASE/kettle
LOG_LEVEL=Minimal

START_DATE=$(date -d "$1" +%s)
END_DATE=$(date -d "$2" +%s)

if [ -z "$START_DATE" -o -z "$END_DATE" ]; then
   echo "Usage: $0 start_date end_date"
   echo "Examples:"
   echo "$ $0 'two days ago' '1 day ago'"
   echo "$ $0 '2013-04-01T00:00:00.000Z' '2013-04-02T00:00:00.000Z'"
   exit 2
fi

LOG=bz_range_$START_DATE.$END_DATE.log
TS="UNIX_TIMESTAMP(CONVERT_TZ(delta_ts, 'US/Pacific','UTC'))"
echo "Processing range from '$1' (${START_DATE}000) to '$2' (${END_DATE}000)."

time ./kitchen.sh -level $LOG_LEVEL -file $KETTLE_JOB -param:BUG_IDS_PARTITION="($TS >= $START_DATE AND $TS <= $END_DATE)" | tee $LOG
