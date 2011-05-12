package com.mozilla.bugzilla_etl.model.attachment;

import java.util.Date;
import java.util.EnumMap;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.model.Entity;
import com.mozilla.bugzilla_etl.model.PersistenceState;
import com.mozilla.bugzilla_etl.model.Version;


public class Attachment extends Entity<Attachment,
                                       AttachmentVersion,
                                       AttachmentFields.Facet> {

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


    @Override
    public AttachmentVersion latest(EnumMap<AttachmentFields.Facet, String> facets,
                                    String creator, Date from, String annotation) {
        Assert.nonNull(facets, creator, from);
        EnumMap<AttachmentFields.Measurement, Long> measurements = new EnumMap<AttachmentFields.Measurement, Long>(AttachmentFields.Measurement.class);
        return new AttachmentVersion(this, facets, measurements, creator, annotation,
                                     from, Version.TO_FUTURE, PersistenceState.NEW);
    }


    private final Long bugId;

}
