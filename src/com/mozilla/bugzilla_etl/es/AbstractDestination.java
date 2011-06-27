package com.mozilla.bugzilla_etl.es;

import java.io.IOException;
import java.io.PrintStream;

import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.action.bulk.BulkRequestBuilder;

import com.mozilla.bugzilla_etl.base.Destination;
import com.mozilla.bugzilla_etl.model.Entity;
import com.mozilla.bugzilla_etl.model.Field;
import com.mozilla.bugzilla_etl.model.PersistenceState;
import com.mozilla.bugzilla_etl.model.Version;


public abstract class AbstractDestination<E extends Entity<E, V, FACET>,
                                          V extends Version<E, V, FACET>,
                                          FACET extends Enum<FACET> & Field>
extends AbstractEsClient implements Destination<E, Exception> {

    public AbstractDestination(PrintStream log, String esNodes, String esCluster) {
        super(log, esNodes, esCluster);
    }

    public static final int BATCH_LIMIT = 1000;
    public static final int TIMEOUT = 25000;

    protected abstract String index();

    public void send(E entity) throws Exception {
        if (bulk == null) {
            bulk = client.prepareBulk();
            batchSize = 0;
        }

        for (final V version : entity) {
            if (version.persistenceState() == PersistenceState.SAVED) continue;
            bulk.add(request(version));
            ++batchSize;
        }

        if (batchSize >= BATCH_LIMIT) flush();
    }

    private void flush() {
        if (bulk == null || batchSize == 0) return;
        final BulkResponse response = bulk.execute().actionGet(TIMEOUT);
        log.format("Loaded %s docs in %s ms, checking...",
                   batchSize, response.getTookInMillis());
        checkResponse(response);
        bulk = null;
    }

    @Override
    public void complete() throws Exception {
        flush();
        final RefreshResponse response =
            client.admin().indices().refresh(new RefreshRequest(index())).actionGet(TIMEOUT);
        log.format("Forced index '%s' to refresh, %s/%s shard report failure.\n",
                   index(),
                   response.getFailedShards(),
                   response.getTotalShards());
    }


    private void checkResponse(final BulkResponse response) {
        if (!response.hasFailures()) {
            log.println("OK");
            return;
        }
        log.println("Bulk load had failures:");
        log.println(response.buildFailureMessage());
    }

    protected abstract IndexRequest request(final V version) throws IOException;

    private int batchSize = 0;
    private BulkRequestBuilder bulk;


}
