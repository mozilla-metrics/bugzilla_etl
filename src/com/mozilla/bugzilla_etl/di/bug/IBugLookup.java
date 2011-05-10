package com.mozilla.bugzilla_etl.di.bug;

import com.mozilla.bugzilla_etl.base.Lookup;
import com.mozilla.bugzilla_etl.model.bug.Bug;

// We have this non-generic interface solely for janino compatibility.
public interface IBugLookup extends Lookup<Bug, Exception> { }
