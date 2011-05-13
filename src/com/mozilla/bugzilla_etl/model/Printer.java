package com.mozilla.bugzilla_etl.model;

import java.io.PrintStream;
import java.util.Map;


/** print bugs for inspection (development/testing) */
public class Printer {

    public static <E extends Entity<E, V, FACET>,
                   V extends Version<E, V, FACET>,
                   FACET extends Enum<FACET> & Field> void print(Entity<E,V,FACET> entity) {
        PrintStream o = System.out;
        o.format("Updating F&M of bug %s\n", entity);
        for (Version<E, V, FACET> v : entity) {
            o.format("Version: %s\n", v);
            o.format("Facets: (%s)\n", v.facets().size());
            for (Map.Entry<FACET, String> entry : v.facets().entrySet()) {
                o.format("%s = '%s'\n", entry.getKey(), entry.getValue());
            }
        }
    }

}
