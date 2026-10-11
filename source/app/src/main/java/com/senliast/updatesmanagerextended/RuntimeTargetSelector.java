package com.senliast.updatesmanagerextended;

import java.util.List;

import io.github.libxposed.service.HookedTarget;

/** Selects an opaque framework target without using its PID as identity. */
final class RuntimeTargetSelector {
    static HookedTarget systemServer(List<HookedTarget> targets) {
        for (HookedTarget target : targets) {
            // LSPosed's framework name may differ from the Linux process name.
            if (target.getUid() == 1000 && ("system".equals(target.getProcessName())
                    || "system_server".equals(target.getProcessName()))) {
                return target;
            }
        }
        return null;
    }
}
