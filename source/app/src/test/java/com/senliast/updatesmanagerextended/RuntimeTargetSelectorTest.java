package com.senliast.updatesmanagerextended;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;

import io.github.libxposed.service.HookedTarget;

import static org.junit.Assert.*;

public class RuntimeTargetSelectorTest {
    private HookedTarget target(int uid, String process, HookedTarget.State state) throws Exception {
        // Framework-owned snapshot handles cannot be constructed by production module code.
        Constructor<HookedTarget> constructor = HookedTarget.class.getDeclaredConstructor(
                long.class, int.class, int.class, String.class, HookedTarget.State.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1L, uid, 123, process, state, 14L);
    }

    @Test
    public void lsposedSystemNameAfterRebootKeepsActiveState() throws Exception {
        HookedTarget running = target(1000, "system", HookedTarget.State.UP_TO_DATE);
        HookedTarget selected = RuntimeTargetSelector.systemServer(Collections.singletonList(running));
        assertSame(running, selected);
        assertEquals(HookedTarget.State.UP_TO_DATE, selected.getState());
    }

    @Test
    public void linuxSystemServerNameIsAlsoRecognized() throws Exception {
        HookedTarget running = target(1000, "system_server", HookedTarget.State.UP_TO_DATE);
        assertSame(running, RuntimeTargetSelector.systemServer(Collections.singletonList(running)));
    }

    @Test
    public void unrelatedProcessesAndAppUidsCannotBecomeReloadTargets() throws Exception {
        HookedTarget app = target(10001, "system", HookedTarget.State.UP_TO_DATE);
        HookedTarget systemUi = target(1000, "com.android.systemui", HookedTarget.State.UP_TO_DATE);
        HookedTarget running = target(1000, "system", HookedTarget.State.STALE);
        assertSame(running, RuntimeTargetSelector.systemServer(Arrays.asList(app, systemUi, running)));
        assertNull(RuntimeTargetSelector.systemServer(Arrays.asList(app, systemUi)));
    }

    @Test
    public void reloadAndFailureStatesArePreservedRatherThanMarkedActive() throws Exception {
        for (HookedTarget.State state : new HookedTarget.State[]{HookedTarget.State.STALE,
                HookedTarget.State.RELOADING, HookedTarget.State.FAILED}) {
            HookedTarget running = target(1000, "system", state);
            assertEquals(state, RuntimeTargetSelector.systemServer(
                    Collections.singletonList(running)).getState());
        }
    }

    @Test
    public void missingTargetIsNotAssumedActive() {
        assertNull(RuntimeTargetSelector.systemServer(Collections.emptyList()));
    }
}
