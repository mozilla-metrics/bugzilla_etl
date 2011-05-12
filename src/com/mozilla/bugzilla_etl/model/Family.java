package com.mozilla.bugzilla_etl.model;

import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields;
import com.mozilla.bugzilla_etl.model.bug.BugFields;

public enum Family {
    ACTIVITY(Fields.Activity.class),
    BUG(BugFields.Bug.class),
    BUG_FACET(BugFields.Facet.class),
    BUG_MEASURE(BugFields.Measurement.class),
    ATTACHMENT(AttachmentFields.Attachment.class),
    ATTACHMENT_FACET(AttachmentFields.Facet.class),
    ATTACHMENT_MEASURE(AttachmentFields.Measurement.class);

    public final Class<? extends Enum<?>> fields;

    <T extends Enum<T> & Field> Family(Class<T> fields) {
        this.fields = fields;
    }
}