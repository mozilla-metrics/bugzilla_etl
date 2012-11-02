# Bugzilla ETL

A set of [Pentaho DI](http://www.pentaho.com/products/data_integration/) jobs to extract bug versions from a bugzilla database and store them in an [elasticsearch](http://www.elasticsearch.org/) index. This ETL drives dashboards for [BMO](http://bugzilla.mozilla.org), for various teams at [Mozilla Corporation](http://www.mozilla.com/en-US/about/). 


## Requirements

* an elasticsearch cluster where you can CRUD the index `bugs`
* a working PDI (a.k.a kettle) installation (free community edition should work fine). Tested with PDI CE 4.3


## Minimal instructions

* Clone this project into a local directory
* Configure the elasticsearch indexes (put a cluster node in place of `localhost`):

    * Optionally: clean out previous indexes:

        > curl -XDELETE 'http://localhost:9200/bugs'


    * Initialize the elasticsearch mappings:

        > curl -XPOST 'http://localhost:9200/bugs' --data @configuration/es/bug_version.json


* Configure Pentaho DI: 
    * add a directory `.kettle` in your `$KETTLE_HOME`
    * there, create a file `kettle.properties`
    * in that file, add settings for `bugs_db_host`, `bugs_db_port`, 
      `bugs_db_user`, `bugs_db_pass` and `bugs_db_name` for your
      bugzilla-database connection.
    * add settings for `ES_NODES`, `ES_CLUSTER`, `ES_INDEX`
* If necessary, modify `bin/import_bugs.sh`, then run it to import the full data set.
* Later on, use `bin/update_bugs_incr.sh` to read incremental modifications from the MySQL database


## Known issues

* Some cases where a user's bugzilla ID changes mid-history for a bug can't be handled automatically, and should be added to `configuration/kettle/bugzilla_aliases.txt`
* Mozilla Bug 804946 causes some trouble with the ETL.  See [Bug 804961](https://bugzilla.mozilla.org/show_bug.cgi?id=804961) for details.
