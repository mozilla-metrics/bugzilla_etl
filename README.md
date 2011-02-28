# Bugzilla ETL

Kettle transformations to reconstruct bug versions from the past and store
them in a Lily repository. This ETL drives Mozillas team- and project-dashboards for [BMO](http://bugzilla.mozilla.org).

## Requirements

Needs an Pentaho Data Integration, an hbase cluster, a lily repository instance, limited read-access to your Bugzilla MySQL database, and (to be useful) a Solr server to push documents (= bugs) to.


## Minimal instructions

* Copy the .example files and adjust them for your setup.
* Run <code>./bin/import FROM TO</code> for the initial batch import, where FROM is the (inclusive) bug number to start at, and TO is the (exclusive) bug ID to stop at. It is recommended to split your import for increased performance (you can run multiple splits simultaneously).
* Later on, use <code>./bin/incremental_update FROM TO</code> to import modifications


## Known issues

* Incremental update will become slower over time as the start date is hardcoded. This is a known flaw and is sitting on the top of the TODO list.

* There are a few locations in the code handling peculiarities of  bugzilla.mozilla.org (for which this ETL was built). I tried to mark all of them up using "BMO:" in comments. Please contact me (or better, [file a Metrics bug](https://bugzilla.mozilla.org/enter_bug.cgi?product=Mozilla%20Metrics)) if you have an interest in this ETL for a different bugzilla installation, and I am sure we can ascertain if these can cause any problems.
