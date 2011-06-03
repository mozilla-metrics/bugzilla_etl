package com.mozilla.bugzilla_etl.es.bug;

import java.util.EnumMap;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.es.Mapping;
import com.mozilla.bugzilla_etl.es.Mapping.BaseFacetMapping;
import com.mozilla.bugzilla_etl.es.Mapping.BaseMapping;
import com.mozilla.bugzilla_etl.model.bug.BugFields;

public class Mappings {

    public static final String TYPE = "bug";

    @SuppressWarnings("serial")
    public static final Mapping<BugFields.Bug> BUG = new BaseMapping<BugFields.Bug>() {{
        conversions = new EnumMap<BugFields.Bug, Conv>(BugFields.Bug.class) {{
            for (BugFields.Bug field : BugFields.Bug.values()) {
                switch (field) {
                    case ID: put(field, Conv.INTEGER); continue;
                    case REPORTED_BY: put(field, Conv.STRING); continue;
                    case CREATION_DATE: put(field, Conv.DATE); continue;
                    default: Assert.unreachable();
                }
            }
        }};
    }};


    @SuppressWarnings("serial")
    static final Mapping<BugFields.Facet> FACET = new BaseFacetMapping<BugFields.Facet>() {{
        conversions = new EnumMap<BugFields.Facet, Conv>(BugFields.Facet.class) {{
            for (BugFields.Facet field : BugFields.Facet.values()) {
                switch (field) {
                    case FLAGS:
                    case CHANGES:
                    case KEYWORDS:
                    case GROUPS:
                    case MODIFIED_FIELDS:
                    case STATUS_WHITEBOARD_ITEMS:
                        put(field, Conv.STRINGLIST); continue;
                    case MAJOR_STATUS_LAST_CHANGED_DATE:
                    case STATUS_LAST_CHANGED_DATE:
                        put(field, Conv.DATE); continue;
                    default:
                        put(field, Conv.STRING);
                }
            };
        }};
    }};


    @SuppressWarnings("serial")
    static final Mapping<BugFields.Measurement> MEASURE = new BaseMapping<BugFields.Measurement>() {{
        conversions = new EnumMap<BugFields.Measurement, Conv>(BugFields.Measurement.class) {{
            for (BugFields.Measurement field : BugFields.Measurement.values()) {
                put(field, Conv.INTEGER);
            }
        }};
    }};
}
