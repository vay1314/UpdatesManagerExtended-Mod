package io.github.vay1314.updatesmanagerextendedmod;

import android.content.SharedPreferences;

import com.google.gson.Gson;

import org.junit.Test;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import static org.junit.Assert.*;

/** Simulates the framework lifecycle; no real system process or device is touched. */
public class HotReloadTest {
    private static final String HOOK_ID = "block_app_updates";

    public static class Source {
        private final String mInstallerPackageName = "example.store";
    }

    public static class Session {
        private final String mPackageName = "example.target";
        private final Source mInstallSource = new Source();
        public void assertPackageConsistentLocked(String name, String signature, long version) {}
    }

    public static class IncompatibleSession {
        public void assertPackageConsistentLocked(String name, String signature, long version) {}
    }

    private static class Handle implements XposedInterface.HookHandle {
        final Method method;
        final String id;
        int replacements;
        int removals;
        XposedInterface.Hooker interceptor;
        Handle replacement;

        Handle(Class<?> owner, String id) throws Exception {
            method = owner.getDeclaredMethod("assertPackageConsistentLocked",
                    String.class, String.class, long.class);
            this.id = id;
        }

        public Executable getExecutable() { return method; }
        public String getId() { return id; }
        public void unhook() { removals++; }
        public XposedInterface.HookHandle replaceHook(XposedInterface.Hooker hooker) {
            replacements++;
            try {
                replacement = new Handle(method.getDeclaringClass(), id);
                replacement.interceptor = hooker;
                return replacement;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    private XposedMain module(Map<String, Object> settings) {
        SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getAll")) return new HashMap<>(settings);
                    throw new AssertionError("Unexpected preferences method: " + method);
                });
        XposedInterface framework = (XposedInterface) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{XposedInterface.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getRemotePreferences")) {
                        assertEquals("Rules", args[0]);
                        return preferences;
                    }
                    if (method.getName().equals("log")) return null;
                    throw new AssertionError("Unexpected framework operation: " + method);
                });
        XposedMain module = new XposedMain();
        // Only the test harness acts as a framework and attaches the mocked interface.
        module.attachFramework(framework, () -> {});
        return module;
    }

    private XposedModuleInterface.HotReloadedParam reloadParam(boolean system, Handle... handles) {
        return (XposedModuleInterface.HotReloadedParam) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{XposedModuleInterface.HotReloadedParam.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isSystemServer")) return system;
                    if (method.getName().equals("getOldHookHandles")) return Arrays.asList(handles);
                    throw new AssertionError("Unexpected reload parameter access: " + method);
                });
    }

    private Map<String, Object> blockingRules() {
        Map<String, Object> rules = new HashMap<>();
        rules.put("moduleStatus", "enabled_immediately");
        rules.put("groups_list", new Gson().toJson(Collections.singletonList(new GroupInfo(
                "Test", "example.store", "example.target", false, "enabled_immediately", 0L))));
        return rules;
    }

    @Test
    public void oldGenerationAllowsReloadWithoutRemovingItsHookOrSavingObjects() {
        XposedModuleInterface.HotReloadingParam param =
                (XposedModuleInterface.HotReloadingParam) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{XposedModuleInterface.HotReloadingParam.class},
                        (proxy, method, args) -> {
                            throw new AssertionError("No old-generation state should be transferred");
                        });
        assertTrue(new XposedMain().onHotReloading(param));
    }

    @Test
    public void replacesExistingHookAtomicallyAndRemovesObsoleteHandles() throws Exception {
        Handle old = new Handle(Session.class, HOOK_ID);
        Handle obsolete = new Handle(Session.class, "obsolete");
        module(blockingRules()).onHotReloaded(reloadParam(true, old, obsolete));
        assertEquals(1, old.replacements);
        assertEquals(0, old.removals);
        assertNotNull(old.replacement.interceptor);
        assertEquals(1, obsolete.removals);
        assertEquals(0, obsolete.replacements);
    }

    @Test
    public void repeatedReloadsReplaceEachGenerationWithoutDuplicatingHooks() throws Exception {
        Handle first = new Handle(Session.class, HOOK_ID);
        module(blockingRules()).onHotReloaded(reloadParam(true, first));
        Handle second = first.replacement;
        module(blockingRules()).onHotReloaded(reloadParam(true, second));
        assertEquals(1, first.replacements);
        assertEquals(1, second.replacements);
        assertNotSame(second.interceptor, second.replacement.interceptor);
    }

    @Test
    public void incompatibleNewCodeLeavesOldHookIntactAndReportsFailure() throws Exception {
        Handle old = new Handle(IncompatibleSession.class, HOOK_ID);
        assertThrows(IllegalStateException.class,
                () -> module(blockingRules()).onHotReloaded(reloadParam(true, old)));
        assertEquals(0, old.replacements);
        assertEquals(0, old.removals);
    }

    @Test
    public void missingHookRequiresRestartInsteadOfReportingSuccessfulReload() throws Exception {
        Handle unrelated = new Handle(Session.class, "unrelated");
        assertThrows(IllegalStateException.class,
                () -> module(blockingRules()).onHotReloaded(reloadParam(true, unrelated)));
        assertEquals(0, unrelated.removals);
    }

    @Test
    public void rejectsReloadOutsideSystemServer() throws Exception {
        Handle old = new Handle(Session.class, HOOK_ID);
        assertThrows(IllegalStateException.class,
                () -> module(blockingRules()).onHotReloaded(reloadParam(false, old)));
        assertEquals(0, old.replacements);
        assertEquals(0, old.removals);
    }

    private static class Invocation {
        final Object[] original = {"example.target", "valid-signature", 1L};
        Object[] received;
        int calls;
        Throwable failure;

        XposedInterface.Chain chain() {
            return (XposedInterface.Chain) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{XposedInterface.Chain.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getThisObject")) return new Session();
                        if (method.getName().equals("getArgs")) return List.of(original);
                        if (method.getName().equals("proceed")) {
                            calls++;
                            received = args == null || args.length == 0
                                    ? original : (Object[]) args[0];
                            if (failure != null) throw failure;
                            return "installation-result";
                        }
                        throw new AssertionError("Unexpected chain operation: " + method);
                    });
        }
    }

    @Test
    public void newInterceptorKeepsBlockingRulesAndReadsLaterChanges() throws Throwable {
        Map<String, Object> rules = blockingRules();
        Handle old = new Handle(Session.class, HOOK_ID);
        module(rules).onHotReloaded(reloadParam(true, old));
        Invocation blocked = new Invocation();
        assertEquals("installation-result", old.replacement.interceptor.intercept(blocked.chain()));
        assertEquals(1, blocked.calls);
        assertEquals("", blocked.received[1]);
        assertEquals("valid-signature", blocked.original[1]);
        rules.put("moduleStatus", "disabled");
        Invocation allowed = new Invocation();
        old.replacement.interceptor.intercept(allowed.chain());
        assertEquals(1, allowed.calls);
        assertSame(allowed.original, allowed.received);
    }

    @Test
    public void originalInstallationExceptionPropagatesExactlyOnce() throws Exception {
        Handle old = new Handle(Session.class, HOOK_ID);
        module(blockingRules()).onHotReloaded(reloadParam(true, old));
        Invocation call = new Invocation();
        call.failure = new IllegalStateException("verification failed");
        Throwable thrown = assertThrows(IllegalStateException.class,
                () -> old.replacement.interceptor.intercept(call.chain()));
        assertSame(call.failure, thrown);
        assertEquals(1, call.calls);
    }

    @Test
    public void malformedRulesAllowInstallationThroughTheNewInterceptor() throws Throwable {
        Map<String, Object> rules = blockingRules();
        rules.put("groups_list", "{invalid");
        Handle old = new Handle(Session.class, HOOK_ID);
        module(rules).onHotReloaded(reloadParam(true, old));
        Invocation call = new Invocation();
        old.replacement.interceptor.intercept(call.chain());
        assertEquals(1, call.calls);
        assertSame(call.original, call.received);
    }
}
