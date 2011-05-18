package com.mozilla.bugzilla_etl.es.bug;

import java.io.IOException;
import java.io.PrintStream;

import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import com.mozilla.bugzilla_etl.es.AbstractDestination;
import com.mozilla.bugzilla_etl.es.bug.Mappings;
import com.mozilla.bugzilla_etl.model.Fields;
import com.mozilla.bugzilla_etl.model.bug.Bug;
import com.mozilla.bugzilla_etl.model.bug.BugFields;
import com.mozilla.bugzilla_etl.model.bug.BugVersion;

import static com.mozilla.bugzilla_etl.es.bug.Mappings.BUG;
import static com.mozilla.bugzilla_etl.es.bug.Mappings.FACET;
import static com.mozilla.bugzilla_etl.es.bug.Mappings.MEASURE;
import static com.mozilla.bugzilla_etl.es.Mappings.VERSION;


public class BugDestination extends AbstractDestination<Bug, BugVersion, BugFields.Facet> {

    public BugDestination(PrintStream log, String esNodes, String esCluster) {
        super(log, esNodes, esCluster);
    }

    protected IndexRequest request(final BugVersion version) throws IOException {

        XContentBuilder out = XContentFactory.jsonBuilder();
        out.startObject();
        BUG.append(out, BugFields.Bug.ID, version.entity().id());
        BUG.append(out, BugFields.Bug.REPORTED_BY, version.entity().reporter());
        BUG.append(out, BugFields.Bug.CREATION_DATE, version.entity().creationDate());
        VERSION.append(out, Fields.Activity.MODIFIED_BY, version.author());
        VERSION.append(out, Fields.Activity.MODIFICATION_DATE, version.from());
        VERSION.append(out, Fields.Activity.EXPIRATION_DATE, version.to());
        VERSION.append(out, Fields.Activity.ANNOTATION, version.annotation());
        for (BugFields.Facet facet : BugFields.Facet.values()) {
            FACET.append(out, facet, version.facets().get(facet));
        }
        for (BugFields.Measurement measure : BugFields.Measurement.values()) {
            MEASURE.append(out, measure, version.measurements().get(measure));
        }
        out.endObject();

        final String id =
            new StringBuilder().append(version.entity().id()).append('.')
            .append(version.measurements().get(BugFields.Measurement.NUMBER)).toString();

        return client.prepareIndex(index(), Mappings.TYPE)
                     .setId(id)
                     .setSource(out).request();
    }

}
