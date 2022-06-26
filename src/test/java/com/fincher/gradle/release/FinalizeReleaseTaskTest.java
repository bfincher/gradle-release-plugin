package com.fincher.gradle.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

class FinalizeReleaseTaskTest extends BaseReleaseTaskTest<FinalizeReleaseTask> {

    private static final String INITIAL_VERSION = "0.0.2";

    FinalizeReleaseTaskTest() {
        super("finalizeRelease", FinalizeReleaseTask.class);
    }

    @BeforeEach
    @Override
    public void beforeEach() throws IOException, GitAPIException {
        super.beforeEach();
        Files.writeString(versionFile, "version=" + INITIAL_VERSION);
    }

    @Test
    void test() throws Exception {
        task.releaseTaskAction();
        verifyResults("0.0.3-SNAPSHOT");
    }

    @Test
    void testVersionFileOverride() throws Exception {
        versionFile = projectDir.resolve("otherFile");
        when(versionFileProperty.getOrElse(any())).thenReturn(versionFile.toFile());

        Files.write(versionFile, Lists.newArrayList("some stuff", "version=0.0.1", "some other stuff"));
        task.setProperty("versionFile", versionFile.toFile());

        task.releaseTaskAction();

        // do extra checking of the file here to ensure that the original structure is
        // still in place
        List<String> lines = Files.readAllLines(versionFile);
        assertEquals(3, lines.size());
        assertEquals("version=0.0.2-SNAPSHOT", lines.get(1));

        verifyResults("0.0.2-SNAPSHOT");
    }

    @Test
    void testVersionKeyValueOverride() throws Exception {
        String key = "otherVersion";
        when(versionKeyValueProperty.getOrElse(anyString())).thenReturn(key);
        task.getVersionKeyValue().set(key);
        Files.write(versionFile, Lists.newArrayList("some stuff", key + " = '0.0.1'", "some other stuff"));

        task.releaseTaskAction();

        // do extra checking of the file here to ensure that the original structure is
        // still in place
        List<String> lines = Files.readAllLines(versionFile);
        assertEquals(3, lines.size());
        assertEquals(key + " = '0.0.2-SNAPSHOT'", lines.get(1));

        verifyResults("0.0.2-SNAPSHOT");
    }

    @Test
    void testOverrideNewVersion() throws Exception {
        task.setNewVersion("1.1.1");
        task.releaseTaskAction();
        verifyResults("1.1.1");
    }

    private void verifyResults(String expectedVersion) throws Exception {
        VersionFile version = VersionFile.load(project, versionFileProperty, versionKeyValueProperty);
        assertEquals(expectedVersion, version.toString());
        verify(git).add();
        verify(addCommand).addFilepattern(versionFile.getFileName().toString());
        verify(addCommand).call();

        verify(git).commit();
        verify(commitCommand).setMessage(String.format("\"Set version after release to %s\"", expectedVersion));
        verify(commitCommand).call();

        verify(git).push();
        verify(pushCommand).setPushTags();
        verify(pushCommand).call();
    }

}
