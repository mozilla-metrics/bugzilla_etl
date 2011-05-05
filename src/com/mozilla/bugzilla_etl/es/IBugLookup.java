package com.mozilla.bugzilla_etl.es;

import org.elasticsearch.ElasticSearchException;

import com.mozilla.bugzilla_etl.base.Bug;
import com.mozilla.bugzilla_etl.base.Lookup;

// We have this non-generic interface solely for janino compatibility.
public interface IBugLookup extends Lookup<Bug, ElasticSearchException> { }
