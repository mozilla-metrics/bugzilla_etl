package com.mozilla.bugzilla_etl.es;

import java.io.PrintStream;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortOrder;

import com.mozilla.bugzilla_etl.base.Lookup;
import com.mozilla.bugzilla_etl.model.Entity;
import com.mozilla.bugzilla_etl.model.Field;
import com.mozilla.bugzilla_etl.model.Version;


public abstract class AbstractLookup<E extends Entity<E, V, FACET>,
                                     V extends Version<E, V, FACET>,
                                     FACET extends Enum<FACET> & Field>
extends AbstractEsClient implements Lookup<E, Exception> {

    public AbstractLookup(PrintStream log, String esNodes) {
        super(log, esNodes);
    }


    @Override
    public E find(Long id) {

        final String index = index();

        // Now we have only the "latest" bug versions, sorted ascending.
        log.format("%s: Searching for id=%d.\n", getClass().getSimpleName(), id);

        final SearchResponse response =
            client.prepareSearch(index).setTypes(type())
            .setSearchType(SearchType.DEFAULT)
            .setTimeout("5s")
            .setQuery(QueryBuilders.fieldQuery(idColumn(), id))
            .setFrom(0).setSize(1024).setExplain(false)
            .addSort(numberColumn(), SortOrder.ASC)
            .execute()
            .actionGet();

        final SearchHits hits = response.getHits();
        if (hits.getTotalHits() == 0) {
            // Now we have only the "latest" bug versions, sorted ascending.
            log.format("LOOKUP: Nothing found for bug %d\n", id);
            return null;
        }

        // Now we have only the "latest" bug versions, sorted ascending.
        log.format("LOOKUP: Reconstructing bug %d from %s versions\n",
                   id, hits.totalHits());

        return reconstruct(hits);
    }


    protected abstract String type();
    protected abstract String idColumn();
    protected abstract String numberColumn();
    protected abstract E reconstruct(SearchHits hits);
}
