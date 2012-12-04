#!/bin/bash

BZ_BASE=/home/mark/mozilla/github/bugzilla_etl
ETL_BASE=$BZ_BASE/transformations
CFG_BASE=$BZ_BASE/configuration/kettle

FIND_ALIASES_JOB=$ETL_BASE/find_aliases.ktr
COMPARE_ALIASES_JOB=$ETL_BASE/detect_new_aliases.ktr
cd ~/mozilla/kettle/data-integration4.3/

LOG_LEVEL=Minimal

INCREMENT=5000

# Extract potential aliases
#echo "Looking for possible aliases on $(date)"
for bug_id in $(seq 0 $INCREMENT 820000); do
   ./pan.sh -level $LOG_LEVEL -file $FIND_ALIASES_JOB -param:BUG_IDS_PARTITION="(bug_id >= ${bug_id} and bug_id < (${bug_id} + $INCREMENT))" > $ETL_BASE/all_aliases_${bug_id}.log
done

# Compare extracted aliases with the production set
time ./pan.sh -level $LOG_LEVEL -file $COMPARE_ALIASES_JOB \
   -param:NEW_ALIASES_FILE=$ETL_BASE/potential_aliases.txt \
   -param:OLD_ALIASES_FILE=$CFG_BASE/bugzilla_aliases.txt \
   -param:ALIAS_UPDATES_FILE=$CFG_BASE/bugzilla_alias_updates.txt > $ETL_BASE/alias_updates$(date +%Y%m%d).log

if [ -s "$CFG_BASE/bugzilla_alias_updates.txt" ]; then
   echo "New aliases were found.  Check $(hostname):$CFG_BASE/bugzilla_alias_updates.txt and log files in $ETL_BASE for details."
   # if we want to include the new aliases in the output:
   # cat $CFG_BASE/bugzilla_alias_updates.txt
else
   echo "No new aliases found on $(date)"
fi
