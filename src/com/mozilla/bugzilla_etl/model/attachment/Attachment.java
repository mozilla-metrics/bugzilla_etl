package com.mozilla.bugzilla_etl.model.attachment;

import java.util.Date;

import com.mozilla.bugzilla_etl.model.Entity;

public class Attachment extends Entity<Attachment, AttachmentVersion> {

    public Attachment(Long id, String reporter, Date creationDate) {
        super(id, reporter, creationDate);
    }

}
