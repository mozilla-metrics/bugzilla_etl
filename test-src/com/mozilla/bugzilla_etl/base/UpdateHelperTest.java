package com.mozilla.bugzilla_etl.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.mozilla.bugzilla_etl.model.bug.Bug;

import static org.junit.Assert.assertEquals;

public class UpdateHelperTest {

    @Test
    public void testWhiteboardItems() {
        final Bug.UpdateHelper helper = new Bug.UpdateHelper();
        final List<Pair<String, String[]>> tests = new ArrayList<Pair<String, String[]>>();

        tests.add(new Pair<String, String[]>("",             new String[]{}));
        tests.add(new Pair<String, String[]>("out",          new String[]{"out"}));
        tests.add(new Pair<String, String[]>("[in]",         new String[]{"in"}));
        tests.add(new Pair<String, String[]>("out out2",     new String[]{"out", "out2"}));
        tests.add(new Pair<String, String[]>("[in] [in2]",   new String[]{"in", "in2"}));
        tests.add(new Pair<String, String[]>("[in][in2]",    new String[]{"in", "in2"}));
        tests.add(new Pair<String, String[]>("[in] out",     new String[]{"in", "out"}));
        tests.add(new Pair<String, String[]>("[in]out",      new String[]{"in", "out"}));

        tests.add(new Pair<String, String[]>("[in]out[in2]",    new String[]{"in", "out", "in2"}));
        tests.add(new Pair<String, String[]>("[in]  out [in2]", new String[]{"in", "out", "in2"}));
        tests.add(new Pair<String, String[]>("out[in]out2",     new String[]{"out", "in", "out2"}));


        tests.add(new Pair<String, String[]>(
                "[hardblocker](?) in-litmus? other-item [some item]",
                new String[]{"hardblocker?", "in-litmus?", "other-item", "some item"}
        ));

        tests.add(new Pair<String, String[]>(
                "outer-item[enclosed item][hardblocker](?) in-litmus? other-item [some item]",
                new String[]{"outer-item", "enclosed item", "hardblocker?", "in-litmus?",
                             "other-item", "some item"}
        ));

        tests.add(new Pair<String, String[]>(
                "blocker, nsbeta1",
                new String[]{"blocker", "nsbeta1"}
        ));

        tests.add(new Pair<String, String[]>(
                "hardblocker, fixed-in-tracemonkey",
                new String[]{"hardblocker", "fixed-in-tracemonkey"}
        ));

        for (Pair<String, String[]> test : tests) {
            System.out.format("Testing whiteboard '%s'\n", test.first());
            assertEquals(Arrays.asList(test.second()), helper.whiteboardItems(test.first()));
        }
    }

}
