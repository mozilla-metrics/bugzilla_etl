# Bugzilla ETL

A set of [PDI](http://www.pentaho.com/products/data_integration/) jobs to extract bug versions from a bugzilla database and store
them in a Lily repository. This ETL drives [BMO](http://bugzilla.mozilla.org)-dashboards for various teams at [MoCo](http://www.mozilla.com/en-US/about/).

## Requirements

* a working PDI (a.k.a kettle) installation (free community edition should work fine)
* on Java 1.6 or higher, and ant
* a [lily repository](http://www.lilyproject.org) instance (this entails [hbase](http://hbase.apache.org/) and [zookeeper](http://zookeeper.apache.org/)
* (limited) read-access to your Bugzilla MySQL database
* a [Solr](http://lucene.apache.org/solr/) server to push *documents* (bugs) to


## Minimal instructions

* Copy the <code>*.example</code> files and adjust them for your setup. 
* Build using <code>bin/install</code> (touches you PDI <code>libext/</code> directory.
* Run <code>bin/import FROM TO</code> for the initial batch import, where FROM is the (inclusive) bug number to start at, and TO is the (exclusive) bug ID to stop at. It is recommended to split your import for increased performance (you can run multiple splits simultaneously).
* Later on, use <code>bin/incremental_update</code> to import modifications


## Known issues

* There are a few locations in the code handling peculiarities of bugzilla.mozilla.org (for which this ETL was built). They should be marked with "BMO" in comments. Please contact me (or better, [file a Metrics bug](https://bugzilla.mozilla.org/enter_bug.cgi?product=Mozilla%20Metrics)) if you have an interest in this ETL for a different bugzilla installation, and I am sure we can ascertain if these can cause any problems.
