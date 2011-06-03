package com.mozilla.bugzilla_etl.es.attachment;

import java.util.EnumMap;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.es.Mapping;
import com.mozilla.bugzilla_etl.es.Mapping.BaseFacetMapping;
import com.mozilla.bugzilla_etl.es.Mapping.BaseMapping;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields;

public class Mappings {

    public static final String TYPE = "attachment";

    @SuppressWarnings("serial")
    public static final Mapping<AttachmentFields.Attachment> ATTACHMENT =
        new BaseMapping<AttachmentFields.Attachment>() {{
        conversions = new EnumMap<AttachmentFields.Attachment, Conv>(AttachmentFields.Attachment.class) {{
            for (AttachmentFields.Attachment field : AttachmentFields.Attachment.values()) {
                switch (field) {
                    case ID: put(field, Conv.INTEGER); continue;
                    case BUG_ID: put(field, Conv.INTEGER); continue;
                    case SUBMITTED_BY: put(field, Conv.STRING); continue;
                    case SUBMISSION_DATE: put(field, Conv.DATE); continue;
                    default: Assert.unreachable();
                }
            }
        }};
    }};


    @SuppressWarnings("serial")
    static final Mapping<AttachmentFields.Facet> FACET =
        new BaseFacetMapping<AttachmentFields.Facet>() {{
        conversions = new EnumMap<AttachmentFields.Facet, Conv>(AttachmentFields.Facet.class) {{
            for (AttachmentFields.Facet field : AttachmentFields.Facet.values()) {
                switch (field) {
                    case CHANGES:
                    case MODIFIED_FIELDS:
                    case GROUPS:
                        put(field, Conv.STRINGLIST); continue;
                    case REQUESTS:
                        put(field, Conv.REQUESTS); continue;
                    case MIMETYPE:
                        put(field, Conv.UNUSED); continue;
                    default:
                        put(field, Conv.BOOLEAN);
                }
            };
        }};
    }};


    @SuppressWarnings("serial")
    static final Mapping<AttachmentFields.Measurement> MEASURE =
        new BaseMapping<AttachmentFields.Measurement>() {{
        conversions = new EnumMap<AttachmentFields.Measurement, Conv>(AttachmentFields.Measurement.class) {{
            for (AttachmentFields.Measurement field : AttachmentFields.Measurement.values()) {
                put(field, Conv.INTEGER);
            }
        }};
    }};
}
