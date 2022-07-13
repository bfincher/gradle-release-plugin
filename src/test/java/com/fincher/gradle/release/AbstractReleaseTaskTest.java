package com.fincher.gradle.release;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AbstractReleaseTaskTest extends BaseReleaseTaskTest<TestAbstractReleaseTask> {

    AbstractReleaseTaskTest() {
        super("abstractReleaseTask", TestAbstractReleaseTask.class);
    }

    @Test
    void basicTest() throws Exception {
        task.releaseTaskAction();
    }

    @Test
    void testMainBranch() throws Exception {
        when(repo.getBranch()).thenReturn("main");
        task.releaseTaskAction();
    }

    @Test
    void testInvalidBranch() throws Exception {
        when(repo.getBranch()).thenReturn("other1");
        assertThrows(IllegalStateException.class, () -> task.releaseTaskAction());
    }

    @ParameterizedTest
    @ValueSource(strings = { "other1", "other2" })
    void testOverrideBranch(String branchName) throws Exception {
        task.setProperty("requiredBranchRegex", "(other1)|(other2)");
        when(repo.getBranch()).thenReturn(branchName);
    }

    @Test
    void testUncommimtedChanges() {
        when(status.hasUncommittedChanges()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> task.releaseTaskAction());
    }

}
