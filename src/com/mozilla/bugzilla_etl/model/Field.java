package com.mozilla.bugzilla_etl.model;


public interface Field {
    Family family();
    String columnName();
}