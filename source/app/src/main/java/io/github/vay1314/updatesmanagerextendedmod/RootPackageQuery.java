package io.github.vay1314.updatesmanagerextendedmod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Bounded read-only root query. No user IDs or package names are supplied by the UI. */
final class RootPackageQuery {
    private static final String COMMAND =
            "[ $(id -u) = 0 ] || exit 1; " +
            "echo @@MAIN; cmd user get-main-user; " +
            "echo @@USERS; cmd user list -v; " +
            "echo @@FALLBACK; pm list users; " +
            "for u in $(pm list users | sed -n 's/.*UserInfo{\\([0-9]*\\):.*/\\1/p'); do " +
            "echo @@PACKAGES:$u; pm list packages -f --user $u; p=$?; " +
            "echo @@SYSTEM:$u; pm list packages -s --user $u; s=$?; " +
            "echo @@DONE:$u:$p:$s; done";

    static UserPackageCatalog load() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("su", "-c", COMMAND).redirectErrorStream(true).start();
        ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "root-package-query");
            thread.setDaemon(true);
            return thread;
        });
        Future<String> output = reader.submit(() -> {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int size;
            while ((size = process.getInputStream().read(buffer)) != -1) {
                if (data.size() + size > 8 * 1024 * 1024) throw new IOException("Package query too large");
                data.write(buffer, 0, size);
            }
            return data.toString(StandardCharsets.UTF_8.name());
        });
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw new IOException("Root query timed out");
            if (process.exitValue() != 0) throw new IOException("Root query denied");
            try {
                UserPackageCatalog result = UserPackageCatalog.parse(output.get(2, TimeUnit.SECONDS));
                if (result.profiles.isEmpty() || result.packages.isEmpty()) {
                    throw new IOException("No cross-profile package information");
                }
                return result;
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                throw new IOException("Cannot read cross-profile package information", e);
            }
        } finally {
            process.destroyForcibly();
            output.cancel(true);
            reader.shutdownNow();
        }
    }
}
