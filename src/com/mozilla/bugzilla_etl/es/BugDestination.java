package com.mozilla.bugzilla_etl.es;

import java.io.IOException;
import java.io.PrintStream;

import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.action.bulk.BulkRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import com.mozilla.bugzilla_etl.base.Bug;
import com.mozilla.bugzilla_etl.base.Destination;
import com.mozilla.bugzilla_etl.base.Fields;
import com.mozilla.bugzilla_etl.base.Fields.Facet;
import com.mozilla.bugzilla_etl.base.Fields.Measurement;
import com.mozilla.bugzilla_etl.base.PersistenceState;
import com.mozilla.bugzilla_etl.base.Version;
import com.mozilla.bugzilla_etl.es.Mapping.BugMapping;

public class BugDestination extends AbstractEsClient
                            implements Destination<Bug, Exception> {

    public static final int BATCH_LIMIT = 1000;
    public static final int TIMEOUT = 15000;

    public BugDestination(PrintStream log, String esNodes) {
      super(log, esNodes);
    }


    public void send(Bug bug) throws Exception {
        if (bulk == null) {
            bulk = client.prepareBulk();
            batchSize = 0;
        }

        for (final Version version : bug) {
            if (version.persistenceState() == PersistenceState.SAVED) continue;
            bulk.add(request(version));
            ++batchSize;
        }

        if (batchSize >= BATCH_LIMIT) flush();
    }


    @Override
    public void flush() throws Exception {
        if (bulk == null || batchSize == 0) return;
        final BulkResponse response = bulk.execute().actionGet(TIMEOUT);
        log.format("Loaded %s docs in %s ms, checking...",
                   batchSize, response.getTookInMillis());
        checkResponse(response);
        bulk = null;
    }


    private void checkResponse(final BulkResponse response) {
        if (!response.hasFailures()) {
            log.println("OK");
            return;
        }
        log.println("Bulk load had failures:");
        log.println(response.buildFailureMessage());
    }


    private IndexRequest request(final Version version) throws IOException {

        XContentBuilder out = XContentFactory.jsonBuilder();
        out.startObject();
        BUG.append(out, Fields.Bug.ID, version.bug().id());
        BUG.append(out, Fields.Bug.REPORTED_BY, version.bug().reporter());
        BUG.append(out, Fields.Bug.CREATION_DATE, version.bug().creationDate());
        VERSION.append(out, Fields.Version.MODIFIED_BY, version.author());
        VERSION.append(out, Fields.Version.MODIFICATION_DATE, version.from());
        VERSION.append(out, Fields.Version.EXPIRATION_DATE, version.to());
        VERSION.append(out, Fields.Version.ANNOTATION, version.annotation());
        for (Facet facet : Facet.values()) {
            FACET.append(out, facet, version.facets().get(facet));
        }
        for (Measurement measure : Measurement.values()) {
            MEASURE.append(out, measure, version.measurements().get(measure));
        }
        out.endObject();

        final String id =
            new StringBuilder().append(version.bug().id()).append('.')
            .append(version.measurements().get(Measurement.NUMBER)).toString();

        return client.prepareIndex(index(), BugMapping.TYPE)
                     .setId(id)
                     .setSource(out).request();
    }


    private int batchSize = 0;
    private BulkRequestBuilder bulk;

}
