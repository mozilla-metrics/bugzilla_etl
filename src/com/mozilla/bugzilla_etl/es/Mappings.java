package com.mozilla.bugzilla_etl.es;

import java.util.EnumMap;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.es.Mapping.BaseMapping;
import com.mozilla.bugzilla_etl.model.Fields;

public class Mappings {

    @SuppressWarnings("serial")
    public static final Mapping<Fields.Activity> VERSION = new BaseMapping<Fields.Activity>() {{
        conversions = new EnumMap<Fields.Activity, Conv>(Fields.Activity.class) {{
            for (Fields.Activity field : Fields.Activity.values()) {
                switch (field) {
                    case ENTITY_ID:
                    case PERSISTENCE_STATE:
                        put(field, Conv.UNUSED); continue;
                    case MODIFIED_BY:
                    case ANNOTATION:
                        put(field, Conv.STRING); continue;
                    case MODIFICATION_DATE:
                    case EXPIRATION_DATE:
                        put(field, Conv.DATE); continue;
                    default:
                        Assert.unreachable();
                }
            }
        }};
    }};

}
