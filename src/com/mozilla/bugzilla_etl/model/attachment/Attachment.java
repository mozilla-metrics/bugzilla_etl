package com.mozilla.bugzilla_etl.model.attachment;

import java.util.Date;
import java.util.EnumMap;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Pair;
import com.mozilla.bugzilla_etl.model.Entity;
import com.mozilla.bugzilla_etl.model.PersistenceState;
import com.mozilla.bugzilla_etl.model.Version;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields.Facet;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields.Measurement;


public class Attachment extends Entity<Attachment, AttachmentVersion, Facet> {

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
    public AttachmentVersion latest(EnumMap<Facet, String> facets, String creator, Date from,
                                    String annotation) {
        Assert.nonNull(facets, creator, from);
        EnumMap<AttachmentFields.Measurement, Long> measurements =
            new EnumMap<AttachmentFields.Measurement, Long>(AttachmentFields.Measurement.class);
        return new AttachmentVersion(this, facets, measurements, creator, annotation,
                                     from, Version.TO_FUTURE, PersistenceState.NEW);
    }


    private final Long bugId;


    public void updateFacetsAndMeasurements(Date now) {
        EnumMap<Facet, String> previousFacets = AttachmentVersion.createFacets();
        long number = 1;
        for (AttachmentVersion version : this) {
            final EnumMap<Facet, String> facets = version.facets();
            final EnumMap<Measurement, Long> measurements = version.measurements();

            // The modified fields are computed after all other facets have been processed.
            final Pair<String, String> changes = changes(previousFacets, facets);
            facets.put(Facet.CHANGES, changes.first());
            facets.put(Facet.MODIFIED_FIELDS, changes.second());

            measurements.put(Measurement.NUMBER, number);
            ++number;
        }

    }

    @Override protected boolean includeInModifiedFields(Facet facet) {

        return facet != Facet.CHANGES && facet != Facet.MODIFIED_FIELDS;
    }


    @Override protected boolean includeInChanges(Facet facet) {
        return true;
    }


    @Override protected boolean isMultivalue(Facet facet) {
        return facet == Facet.REQUESTS;
    }


    @SuppressWarnings("serial") 
    @Override 
    public EnumMap<Facet, String> createFacets() {
        return new EnumMap<Facet, String>(Facet.class) {{
            for (Facet f : Facet.values()) put(f, null);
        }};
    }


    @SuppressWarnings("serial") 
    public EnumMap<Measurement, Long> createMeasurements() {
        return new EnumMap<Measurement, Long>(Measurement.class) {{
            for (Measurement m : Measurement.values()) put(m, null);
        }};
    }

}
