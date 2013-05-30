#!/bin/bash

if [ -z "$KETTLE_BASE" ]; then
   KETTLE_BASE=/opt/pentaho/kettle/etl/bugzilla_etl
fi

if [ -z "$TARGET" ]; then
   TARGET=$(date -u "+%Y-%m-%d 00:00:00")
fi

TS_FILE=$KETTLE_BASE/BZ_LAST_RUN


if [ -f $TS_FILE ]; then
   CURR_TS_MS=$(cat $TS_FILE)
   CURR_TS_S=${CURR_TS_MS%[0-9][0-9][0-9]}
   CURR_V=$(date -u -d @$CURR_TS_S +%Y-%m-%dT%H:%M:%S)
   NEW_TS=$(date -u -d "$TARGET" +%s)000
   read -p "Do you want to rewind '$TS_FILE' from its current value: $CURR_V ($CURR_TS_MS) to $TARGET ($NEW_TS)? (y/N) > " confirm
   case "$confirm" in
     y|Y )
       echo "Rewinding..."
       echo $NEW_TS > $TS_FILE
       echo "New contents of $TS_FILE:"
       cat $TS_FILE
       ;;
     *) echo "Abort mission!" ;;
    esac
else
   echo "No BZ_LAST_RUN file found at '$TS_FILE'.  Specify a correct 'KETTLE_BASE' var and try again."
fi
