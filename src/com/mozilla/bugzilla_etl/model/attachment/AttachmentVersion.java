package com.mozilla.bugzilla_etl.model.attachment;

import java.util.Date;

import com.mozilla.bugzilla_etl.model.PersistenceState;
import com.mozilla.bugzilla_etl.model.Version;

public class AttachmentVersion extends Version<Attachment, AttachmentVersion> {

    public AttachmentVersion(Attachment bug, String author, String maybeAnnotation, Date from,
            Date to, PersistenceState persistenceState) {
        super(bug, author, maybeAnnotation, from, to, persistenceState);
        // TODO Auto-generated constructor stub
    }

    @Override public AttachmentVersion update(String newAnnotation, Date newTo) {
        // TODO Auto-generated method stub
        return null;
    }

}
