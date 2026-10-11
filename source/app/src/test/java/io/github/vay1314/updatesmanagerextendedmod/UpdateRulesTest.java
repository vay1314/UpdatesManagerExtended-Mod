package io.github.vay1314.updatesmanagerextendedmod;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateRulesTest {
    private static final long NOW = 1000L;
    private static final String APP = "example.target";
    private static final String STORE = "example.store";

    private GroupInfo group(boolean allSources, String status, Long resumeTime) {
        return new GroupInfo("Test", STORE, APP, allSources, status, resumeTime);
    }

    private Map<String, Object> settings(String status, Long resumeTime, GroupInfo... groups) {
        Map<String, Object> result = new HashMap<>();
        result.put("moduleStatus", status);
        if (resumeTime != null) result.put("moduleStatusTime", resumeTime);
        result.put("groups_list", new Gson().toJson(Arrays.asList(groups)));
        return result;
    }

    @Test
    public void disabledModuleAllowsUpdates() {
        assertFalse(UpdateRules.shouldBlock(settings("disabled", 0L,
                group(true, "enabled_immediately", 0L)), APP, STORE, NOW));
    }

    @Test
    public void moduleResumesExactlyAtDeadline() {
        Map<String, Object> rules = settings("enabled_since", NOW,
                group(true, "enabled_immediately", 0L));
        assertFalse(UpdateRules.shouldBlock(rules, APP, STORE, NOW - 1));
        assertTrue(UpdateRules.shouldBlock(rules, APP, STORE, NOW));
    }

    @Test
    public void groupResumesExactlyAtDeadline() {
        Map<String, Object> rules = settings("enabled_immediately", 0L,
                group(true, "enabled_since", NOW));
        assertFalse(UpdateRules.shouldBlock(rules, APP, STORE, NOW - 1));
        assertTrue(UpdateRules.shouldBlock(rules, APP, STORE, NOW));
    }

    @Test
    public void pausedRulesWithoutDeadlineStayInactive() {
        assertFalse(UpdateRules.shouldBlock(settings("enabled_since", null,
                group(true, "enabled_immediately", 0L)), APP, STORE, NOW));
        assertFalse(UpdateRules.shouldBlock(settings("enabled_immediately", 0L,
                group(true, "enabled_since", null)), APP, STORE, NOW));
    }

    @Test
    public void selectedSourceAndPackageMustBothMatchExactly() {
        Map<String, Object> rules = settings("enabled_immediately", 0L,
                group(false, "enabled_immediately", 0L));
        assertTrue(UpdateRules.shouldBlock(rules, APP, STORE, NOW));
        assertFalse(UpdateRules.shouldBlock(rules, APP, "other.store", NOW));
        assertFalse(UpdateRules.shouldBlock(rules, APP + ".suffix", STORE, NOW));
        assertFalse(UpdateRules.shouldBlock(rules, "example", STORE, NOW));
        assertFalse(UpdateRules.shouldBlock(rules, APP, null, NOW));
    }

    @Test
    public void allSourcesAlsoBlocksUnknownSource() {
        Map<String, Object> rules = settings("enabled_immediately", 0L,
                group(true, "enabled_immediately", 0L));
        assertTrue(UpdateRules.shouldBlock(rules, APP, "other.store", NOW));
        assertTrue(UpdateRules.shouldBlock(rules, APP, null, NOW));
        assertFalse(UpdateRules.shouldBlock(rules, "other.app", STORE, NOW));
    }

    @Test
    public void inactiveAndNullGroupsDoNotOverrideActiveGroup() {
        Map<String, Object> rules = settings("enabled_immediately", 0L, null,
                group(true, "disabled", 0L), group(false, "enabled_immediately", 0L));
        assertTrue(UpdateRules.shouldBlock(rules, APP, STORE, NOW));
        assertFalse(UpdateRules.shouldBlock(rules, APP, "other.store", NOW));
    }

    @Test
    public void emptyMissingAndIncompleteSettingsAllowUpdates() {
        assertFalse(UpdateRules.shouldBlock(new HashMap<>(), APP, STORE, NOW));
        Map<String, Object> rules = settings("enabled_immediately", 0L);
        assertFalse(UpdateRules.shouldBlock(rules, APP, STORE, NOW));
        rules.put("groups_list", "null");
        assertFalse(UpdateRules.shouldBlock(rules, APP, STORE, NOW));
        rules.put("groups_list", "[{}]");
        assertFalse(UpdateRules.shouldBlock(rules, APP, STORE, NOW));
        rules.remove("groups_list");
        assertFalse(UpdateRules.shouldBlock(rules, APP, STORE, NOW));
    }

    @Test
    public void missingTargetDoesNotMatchEmptyPackageList() {
        Map<String, Object> rules = settings("enabled_immediately", 0L,
                group(true, "enabled_immediately", 0L));
        assertFalse(UpdateRules.shouldBlock(rules, null, STORE, NOW));
        assertFalse(UpdateRules.shouldBlock(rules, "", STORE, NOW));
    }

    @Test(expected = JsonParseException.class)
    public void malformedRulesAreReportedToTheProtectiveInterceptor() {
        Map<String, Object> rules = settings("enabled_immediately", 0L);
        rules.put("groups_list", "{invalid");
        UpdateRules.shouldBlock(rules, APP, STORE, NOW);
    }

    @Test
    public void replacingSnapshotRemovesOldRules() {
        Map<String, Object> rules = settings("enabled_immediately", 0L,
                group(true, "enabled_immediately", 0L));
        assertTrue(UpdateRules.shouldBlock(rules, APP, STORE, NOW));
        rules.put("groups_list", "[]");
        assertFalse(UpdateRules.shouldBlock(rules, APP, STORE, NOW));
    }
}
