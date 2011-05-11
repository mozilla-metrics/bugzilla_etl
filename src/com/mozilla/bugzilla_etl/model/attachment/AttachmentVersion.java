package com.mozilla.bugzilla_etl.model.attachment;

import java.util.Date;
import java.util.EnumMap;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.model.PersistenceState;
import com.mozilla.bugzilla_etl.model.Version;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields.Facet;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields.Measurement;


public class AttachmentVersion extends Version<Attachment, AttachmentVersion> {

    @Override public AttachmentVersion update(String newAnnotation, Date newTo) {
        PersistenceState newState = PersistenceState.NEW;
        if (persistenceState == PersistenceState.SAVED) newState = PersistenceState.DIRTY;
        if (newAnnotation == null) newAnnotation = this.annotation;
        if (newTo == null) newTo = this.to;
        return new AttachmentVersion(entity, facets.clone(),
                                     measurements.clone(), author, newAnnotation,
                                     from, newTo, newState);
    }

    public AttachmentVersion predecessor(EnumMap<AttachmentFields.Facet, String> facets,
                                         String author,
                                         Date from,
                                         String maybeAnnotation) {
        Assert.nonNull(author, facets, from);
        if (maybeAnnotation == null) maybeAnnotation = "";
        return new AttachmentVersion(this.entity,
                                     facets,
                                     AttachmentVersion.createMeasurements(),
                                     author,
                                     maybeAnnotation,
                                     from,
                                     this.from,
                                     PersistenceState.NEW);
    }

    /** Helps to create fields for new versions. */
    public static EnumMap<AttachmentFields.Facet, String> createFacets() {
        return new EnumMap<AttachmentFields.Facet, String>(AttachmentFields.Facet.class);
    }

    /** Helps to create fields for new versions. */
    public static EnumMap<AttachmentFields.Measurement, Long> createMeasurements() {
        return new EnumMap<AttachmentFields.Measurement, Long>(AttachmentFields.Measurement.class);
    }

    /** Create a new version from a complete set of information. */
    public AttachmentVersion(final Attachment attachment,
                             final EnumMap<AttachmentFields.Facet, String> facets,
                             final EnumMap<AttachmentFields.Measurement, Long> measurements,
                             final String author,
                             String maybeAnnotation,
                             final Date from,
                             final Date to,
                             final PersistenceState persistenceState) {
        super(attachment, author, maybeAnnotation, from, to, persistenceState);
        Assert.nonNull(facets, measurements);
        if (!from.before(to)) {
            Assert.unreachable("Faulty expiration range on #%s! DST bug? From: %s, to: %s",
                               attachment, from, to);
        }
        if (maybeAnnotation == null) maybeAnnotation = "";
        this.facets = facets.clone();
        this.measurements = measurements.clone();
    }

    private final EnumMap<Facet, String> facets;
    private final EnumMap<Measurement, Long> measurements;
    public EnumMap<AttachmentFields.Facet, String> facets() { return facets; }
    public EnumMap<AttachmentFields.Measurement, Long> measurements() { return measurements; }

}
