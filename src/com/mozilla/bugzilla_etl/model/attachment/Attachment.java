package com.mozilla.bugzilla_etl.model.attachment;

import java.util.Date;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.model.Entity;


public class Attachment extends Entity<Attachment, AttachmentVersion> {

    public Attachment(Long id, Long bugId, String reporter, Date creationDate) {
        super(id, reporter, creationDate);
        Assert.nonNull(bugId);
        this.bugId = bugId;
    }


    public Long bugId() {
        return bugId;
    }

    @Override
    public String toString() {
        final StringBuilder versionsVis = new StringBuilder(versions.size());
        for (final AttachmentVersion version : versions) {
            switch (version.persistenceState()) {
                case SAVED: versionsVis.append('.'); break;
                case DIRTY: versionsVis.append('#'); break;
                case NEW: versionsVis.append('*'); break;
                default: Assert.unreachable();
            }
        }
        return String.format("{attachment id=%s, bug_id=%s, reporter=%s, versions=%s (.saved #dirty *new)}",
                             id, reporter, versionsVis.toString());
    }

    private final Long bugId;
}
