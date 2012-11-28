#!/bin/bash

KETTLE_JOB=~/mozilla/github/bugzilla_etl/transformations/find_aliases.ktr
cd ~/mozilla/kettle/data-integration4.3/

LOG_LEVEL=Minimal

INCREMENT=5000

for bug_id in $(seq 0 $INCREMENT 820000); do
   echo "Processing batch ${bug_id}"
   time ./pan.sh -level $LOG_LEVEL -file $KETTLE_JOB -param:BUG_IDS_PARTITION="(bug_id >= ${bug_id} and bug_id < (${bug_id} + $INCREMENT))" > $(dirname $KETTLE_JOB)/all_aliases_${bug_id}.log
done
