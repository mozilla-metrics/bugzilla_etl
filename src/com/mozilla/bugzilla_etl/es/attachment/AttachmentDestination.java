package com.mozilla.bugzilla_etl.es.attachment;

import java.io.IOException;
import java.io.PrintStream;

import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import com.mozilla.bugzilla_etl.es.AbstractDestination;
import com.mozilla.bugzilla_etl.model.Fields;
import com.mozilla.bugzilla_etl.model.attachment.Attachment;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields.Facet;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentVersion;

import static com.mozilla.bugzilla_etl.es.attachment.Mappings.ATTACHMENT;
import static com.mozilla.bugzilla_etl.es.attachment.Mappings.FACET;
import static com.mozilla.bugzilla_etl.es.attachment.Mappings.MEASURE;
import static com.mozilla.bugzilla_etl.es.Mappings.VERSION;


public class AttachmentDestination extends AbstractDestination<Attachment, AttachmentVersion, Facet> {

    public AttachmentDestination(PrintStream log, String esNodes) {
        super(log, esNodes);
    }

    @Override protected IndexRequest request(AttachmentVersion version) throws IOException {

        XContentBuilder out = XContentFactory.jsonBuilder();
        out.startObject();
        ATTACHMENT.append(out, AttachmentFields.Attachment.ID,
                          version.entity().id());
        ATTACHMENT.append(out, AttachmentFields.Attachment.BUG_ID,
                          version.entity().bugId());
        ATTACHMENT.append(out, AttachmentFields.Attachment.SUBMITTED_BY,
                          version.entity().reporter());
        ATTACHMENT.append(out, AttachmentFields.Attachment.SUBMISSION_DATE,
                          version.entity().creationDate());
        VERSION.append(out, Fields.Activity.MODIFIED_BY, version.author());
        VERSION.append(out, Fields.Activity.MODIFICATION_DATE, version.from());
        VERSION.append(out, Fields.Activity.EXPIRATION_DATE, version.to());
        VERSION.append(out, Fields.Activity.ANNOTATION, version.annotation());
        for (AttachmentFields.Facet facet : AttachmentFields.Facet.values()) {
            FACET.append(out, facet, version.facets().get(facet));
        }
        for (AttachmentFields.Measurement measure : AttachmentFields.Measurement.values()) {
            MEASURE.append(out, measure, version.measurements().get(measure));
        }
        out.endObject();

        final String id =
            new StringBuilder().append(version.entity().id()).append('.')
            .append(version.measurements().get(AttachmentFields.Measurement.NUMBER)).toString();

        return client.prepareIndex(index(), Mappings.TYPE)
                     .setId(id)
                     .setSource(out).request();
    }
}
