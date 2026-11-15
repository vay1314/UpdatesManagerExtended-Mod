package io.github.vay1314.updatesmanagerextendedmod;

import org.junit.Test;

import static org.junit.Assert.*;

public class UserPackageCatalogTest {
    private static final String USERS = "@@MAIN\n23\n@@USERS\n"
            + "0: id=23, name=Owner, type=full.SECONDARY, flags=ADMIN|MAIN (current)\n"
            + "1: id=77, name=Clone, type=profile.CLONE, flags=PROFILE (parentId=23)\n"
            + "2: id=112, name=Office, type=profile.MANAGED, flags=PROFILE\n"
            + "@@FALLBACK\nUserInfo{23:Owner:4010}\nUserInfo{77:Clone:1010}\nUserInfo{112:Office:1030}\n";

    @Test
    public void arbitraryUserIdsAndSharedPackageAreMerged() {
        UserPackageCatalog catalog = UserPackageCatalog.parse(USERS
                + "@@PACKAGES:23\npackage:/data/app/ab==/base.apk=example.shared\n"
                + "@@SYSTEM:23\n@@DONE:23:0:0\n"
                + "@@PACKAGES:77\npackage:/data/app/ab==/base.apk=example.shared\n"
                + "package:/data/app/clone/base.apk=example.cloneonly\n@@SYSTEM:77\n@@DONE:77:0:0\n"
                + "@@PACKAGES:112\npackage:/data/app/work/base.apk=example.work\n@@SYSTEM:112\n@@DONE:112:0:0\n");
        assertTrue(catalog.complete);
        assertEquals(3, catalog.packages.size());
        assertEquals(2, catalog.packages.get("example.shared").users.size());
        assertTrue(catalog.profiles.get(23).main);
        assertFalse(catalog.profiles.get(77).main);
        assertTrue(catalog.profiles.get(77).cloneProfile());
        assertTrue(catalog.profiles.get(112).workProfile());
        assertEquals("/data/app/ab==/base.apk", catalog.packages.get("example.shared").apkPath);
        assertEquals(1, catalog.profilesFor(catalog.packages.get("example.cloneonly")).size());
    }

    @Test
    public void legacyFlagsIdentifyMainWithoutModernUserCommand() {
        UserPackageCatalog catalog = UserPackageCatalog.parse("@@MAIN\nUnknown command\n@@USERS\n"
                + "@@FALLBACK\nUserInfo{34:Main:4010}\nUserInfo{56:Other:10}\n"
                + "@@PACKAGES:34\npackage:/system/app/test.apk=example.system\n"
                + "@@SYSTEM:34\npackage:example.system\n@@DONE:34:0:0\n"
                + "@@PACKAGES:56\n@@SYSTEM:56\n@@DONE:56:0:0\n");
        assertTrue(catalog.complete);
        assertTrue(catalog.profiles.get(34).main);
        assertFalse(catalog.profiles.get(56).main);
        assertTrue(catalog.packages.get("example.system").system);
    }

    @Test
    public void explicitMainUserOverridesLegacyPrimaryFlag() {
        UserPackageCatalog catalog = UserPackageCatalog.parse("@@MAIN\n52\n@@FALLBACK\n"
                + "UserInfo{11:Headless:801}\nUserInfo{52:Human:400}\n");
        assertFalse(catalog.profiles.get(11).main);
        assertTrue(catalog.profiles.get(52).main);
    }

    @Test
    public void partialQueriesKeepUsableEntriesButReportIncomplete() {
        UserPackageCatalog catalog = UserPackageCatalog.parse(USERS
                + "@@PACKAGES:23\npackage:/data/app/a/base.apk=example.visible\n"
                + "@@SYSTEM:23\n@@DONE:23:0:0\n"
                + "@@PACKAGES:77\nError: user locked\n@@SYSTEM:77\n@@DONE:77:1:1\n");
        assertFalse(catalog.complete);
        assertTrue(catalog.packages.containsKey("example.visible"));
        assertFalse(UserPackageCatalog.parse("permission denied").complete);
    }

    @Test
    public void invalidRowsAreIgnoredAndPrivateProfilesRecognized() {
        UserPackageCatalog catalog = UserPackageCatalog.parse("@@USERS\n"
                + "0: id=62, name=Hidden, type=profile.PRIVATE, flags=PROFILE\n@@PACKAGES:62\n"
                + "package:invalid\npackage:/a/base.apk=bad;name\npackage:/a/base.apk=valid.app\n"
                + "@@SYSTEM:62\n@@DONE:62:0:0\n");
        assertEquals(1, catalog.packages.size());
        assertTrue(catalog.profiles.get(62).privateProfile());
    }
}
