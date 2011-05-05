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

public class BugDestination extends AbstractEsClient implements Destination<Bug, Exception> {

    public static final int BATCH = 5000;
    public static final int TIMEOUT = 5000;
    
    public BugDestination(PrintStream log, String esQuorum) {
      super(log, esQuorum);
    }

      
    public void send(Bug bug) throws Exception {
        if (bulk == null) {
            bulk = client.prepareBulk();
            buffered = 0;
        }
        
        for (final Version version : bug) {
            if (version.persistenceState() == PersistenceState.SAVED) continue;
            bulk.add(request(version));
            ++buffered;
        }
        
        if (buffered >= BATCH) flush();
    }

    
    @Override 
    public void flush() throws Exception {
        if (bulk == null) return;
        BulkResponse response = bulk.execute().actionGet(TIMEOUT);
        log.format("Indexed bulk of %d records in %d ms.\n",
                   buffered, response.getTookInMillis());
        bulk = null;
    }
    
    private IndexRequest request(Version ver) throws IOException {
      XContentBuilder out = XContentFactory.jsonBuilder();
      out.startObject();
      BUG.append(out, Fields.Bug.ID, ver.bug().id());
      BUG.append(out, Fields.Bug.REPORTED_BY, ver.bug().reporter());
      BUG.append(out, Fields.Bug.CREATION_DATE, ver.bug().creationDate());
      VERSION.append(out, Fields.Version.MODIFIED_BY, ver.author());
      VERSION.append(out, Fields.Version.MODIFICATION_DATE, ver.to());
      VERSION.append(out, Fields.Version.EXPIRATION_DATE, ver.from());
      VERSION.append(out, Fields.Version.ANNOTATION, ver.annotation());
      for (Facet facet : Facet.values()) {
          FACET.append(out, facet, ver.facets().get(facet));
      }
      for (Measurement measure : Measurement.values()) {
          MEASURE.append(out, measure, ver.measurements().get(measure));
      }
      out.endObject();
      
      final String id = 
          ver.bug().id() + "." + ver.measurements().get(Measurement.NUMBER);
      return client.prepareIndex(index(), BugMapping.TYPE)
             .setId(id)
             .setSource(out).request();
    }
    
    private int buffered = 0;
    private BulkRequestBuilder bulk;
    
}
