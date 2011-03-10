package com.mozilla.bugzilla_etl.base;


public interface Failable<E extends Exception> {
    void tryIt() throws E, InterruptedException;
}
