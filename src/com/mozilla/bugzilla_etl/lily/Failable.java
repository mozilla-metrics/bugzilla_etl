package com.mozilla.bugzilla_etl.lily;


public interface Failable<E extends Exception> {
    void tryIt() throws E, InterruptedException;
}
