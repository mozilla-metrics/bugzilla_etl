# Bugzilla ETL

A set of [Pentaho DI](http://www.pentaho.com/products/data_integration/) jobs to extract bug versions from a bugzilla database and store them in an [elasticsearch](http://www.elasticsearch.org/) index. This ETL drives dashboards for [BMO](http://bugzilla.mozilla.org), for various teams at [Mozilla Corp.](http://www.mozilla.com/en-US/about/). 

A previous version (`v1.0`, currently in use at MoCo) stores data in a Solr index via a [lily content repository](http://www.lilyproject.org). Any fixes for that are added on the `lilydest` branch. 


## Requirements

* an elasticsearch cluster where you can CRUD the indexes `bugs` and `attachments`
* a working PDI (a.k.a kettle) installation (free community edition should work fine). Tested with PDI CE 4.10
* Java 1.6 or higher, and Apache Ant to install the ETL


## Minimal instructions

* Clone this project into a local directory
* Configure the elasticsearch indexes (put a cluster node in place of `localhost`):
** Optionally: clean out previous indexes:

	> curl -XDELETE 'http://localhost:9200/bugs'
	> curl -XDELETE 'http://localhost:9200/attachments' ; echo

** Initialize the elasticsearch mappings:

    > curl -XPOST 'http://localhost:9200/bugs' --data @configuration/es/bugs.json
    > curl -XPOST 'http://localhost:9200/attachments' --data @configuration/es/attachments.json; echo

* Copy/Rename the `*.example` files to lose the suffix, and adjust them for your setup. 
* Using `bin/install`, build the ETL and install it into your PDI `libext/` directory.
* Configure Pentaho DI: 
* Run `bin/import FROM TO` for the initial batch import, where FROM is the (inclusive) bug number to start at, and TO is the (exclusive) bug ID to stop at. It is recommended to split your import across multiple clients. This makes the ETL queries to Bugzilla return much more quickly, and saves on loading time by parallelizing bulk loads. Of course, this mostly makes sense if you have a corresponding number of
* Later on, use `bin/update` to read modifications from the MySQL database


## Known issues

* There are a few locations in the code handling peculiarities of bugzilla.mozilla.org (for which this ETL was built). They should be marked with "BMO" in comments. Please contact me (or better, [file a Metrics bug](https://bugzilla.mozilla.org/enter_bug.cgi?product=Mozilla%20Metrics)) if you have an interest in this ETL for a different bugzilla installation, and we’ll take care of those.
