package com.mozilla.bugzilla_etl.model.attachment;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.mozilla.bugzilla_etl.model.Family;
import com.mozilla.bugzilla_etl.model.Field;


public class AttachmentFields {

    /** Meta data about attachment activity. Most of the time reviews. */
    public static enum Activity implements Field {
        ATTACHMENT_ID("activity_attachment_id"),
        MODIFIED_BY,
        MODIFICATION_DATE,
        EXPIRATION_DATE,
        ANNOTATION,
        PERSISTENCE_STATE;

        public String columnName;

        @Override
        public String columnName() { return columnName; }

        @Override
        public Family family() { return Family.BUG_ACTIVITY; }

        Activity(String name) { columnName = name; }
        Activity() { columnName = name().toLowerCase(); }
    }


    /** Version-independent data on an attachment. */
    public static enum Attachment implements Field {
        ID("attachment_id"),
        BUG_ID,
        SUBMITTED_BY,
        SUBMISSION_DATE;

        public String columnName;

        @Override
        public String columnName() { return columnName; }

        @Override
        public Family family() { return Family.BUG; }

        Attachment(String name) { columnName = name; }
        Attachment() { columnName = name().toLowerCase(); }
    }


    /** Attachment facets. */
    public static enum Facet implements Field {
        CHANGES(true),
        IS_OBSOLETE,
        IS_PATCH,
        IS_URL,
        MIME_TYPE,
        MODIFIED_FIELDS(true),
        REQUESTS;

        public static enum Column { LATEST, FROM, TO, RESULT }
        public final Map<Facet.Column, String> columnNames;

        @Override
        public String columnName() { return columnNames.get(Column.RESULT); }

        @Override
        public Family family() { return Family.FACET; }

        public final boolean isComputed;

        /**
         * If initialized with a column name of <tt>null</tt>, this facet is
         * computed and not present in activities input.
         * @param columnName
         */
        Facet(boolean isComputed) {
            this.isComputed = isComputed;
            Map<Facet.Column, String> names = new EnumMap<Column, String>(Facet.Column.class);
            final String columnName= name().toLowerCase();
            if (!isComputed) {
                names.put(Column.LATEST, columnName);
                names.put(Column.FROM, columnName + "_from");
                names.put(Column.TO, columnName + "_to");
            }
            names.put(Column.RESULT, columnName);
            columnNames = Collections.unmodifiableMap(names);
        }

        Facet() { this(false); }
    }


    /** Attachment measurements. */
    public static enum Measurement implements Field {

        /** Attachment version number. */
        NUMBER;

        public String columnName;

        @Override
        public String columnName() { return columnName; }

        @Override
        public Family family() { return Family.MEASUREMENT; }

        Measurement(String name) { columnName = name; }
        Measurement() { columnName = name().toLowerCase(); }
    }
}
