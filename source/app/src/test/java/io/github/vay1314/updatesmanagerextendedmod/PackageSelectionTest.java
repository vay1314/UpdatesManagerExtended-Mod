package io.github.vay1314.updatesmanagerextendedmod;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackageSelectionTest {
    @Test
    public void savedPackagesRemainEditableWhenQueryCannotSeeThem() {
        Set<String> saved = PackageSelection.parse("work.only,uninstalled.app,visible.app");
        List<String> visible = new ArrayList<>(Collections.singletonList("visible.app"));
        PackageSelection.includeSaved(visible, saved, name -> name, name -> name);
        assertEquals(Arrays.asList("visible.app", "work.only", "uninstalled.app"), visible);
        assertEquals(3, saved.size());
    }

    @Test
    public void reinstallDoesNotDuplicateSavedEntry() {
        Set<String> saved = PackageSelection.parse("example.app");
        List<String> visible = new ArrayList<>(Collections.singletonList("example.app"));
        PackageSelection.includeSaved(visible, saved, name -> name, name -> {
            throw new AssertionError("Installed entries must keep their label and icon");
        });
        assertEquals(Collections.singletonList("example.app"), visible);
    }

    @Test
    public void removingMissingRuleStopsItFromAppearingNextLoad() {
        Set<String> saved = PackageSelection.parse("missing.app");
        saved.remove("missing.app");
        List<String> visible = new ArrayList<>();
        PackageSelection.includeSaved(visible, saved, name -> name, name -> name);
        assertTrue(visible.isEmpty());
    }

    @Test
    public void legacyEmptyAndRepeatedEntriesDoNotProduceBlankRows() {
        assertTrue(PackageSelection.parse(null).isEmpty());
        assertTrue(PackageSelection.parse(", ,").isEmpty());
        assertEquals(Collections.singleton("example.app"),
                PackageSelection.parse(",example.app, example.app ,"));
    }
}
