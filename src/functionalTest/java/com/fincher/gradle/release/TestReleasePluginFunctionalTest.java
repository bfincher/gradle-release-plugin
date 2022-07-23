package com.fincher.gradle.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Set;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * A simple functional test for the 'testPlugin.greeting' plugin.
 */
class TestReleasePluginFunctionalTest {

    private Path projectDir;
    private Path gitRepoDir;
    private Path buildFile;
    private Path settingsFile;
    private Path gradlePropertiesFile;
    private Path versionFile;
    private Path gitRepoBareDir;
    private String versionKeyValue;
    private GradleRunner runner;
    private Git git;

    @BeforeEach
    public void beforeEach() throws Exception {
        projectDir = recursivelyDeleteDir(Paths.get("build", "testProjectDir"));
        gitRepoBareDir = createEmptyDir(Paths.get("build", "testGitBare"));
        gitRepoDir = projectDir;
        buildFile = projectDir.resolve("build.gradle");
        settingsFile = projectDir.resolve("settings.gradle");
        gradlePropertiesFile = projectDir.resolve("gradle.properties");
        versionFile = gradlePropertiesFile;
        git = initGit();
        versionKeyValue = "version";
        runner = initRunner();

        Files.writeString(settingsFile, "");
        Files.writeString(buildFile, "plugins {" + "  id('com.fincher.release')" + "}");
        Files.writeString(gradlePropertiesFile, versionKeyValue + " = 0.0.1-SNAPSHOT");

        gitAddInitialFiles(git);
    }

    @Test
    void testMajorRelease() throws IOException, GitAPIException {
        BuildResult result = runWithArguments("prepareRelease", "--releaseType", "MAJOR");
        verifyPrepareReleaseResults(result, "1.0.0");

        result = runWithArguments("finalizeRelease");
        verifyFinalizeReleaseResults("1.0.1-SNAPSHOT");
    }

    @Test
    void testMinorRelease() throws IOException, GitAPIException {
        BuildResult result = runWithArguments("prepareRelease", "--releaseType", "MINOR");
        verifyPrepareReleaseResults(result, "0.1.0");

        result = runWithArguments("finalizeRelease");
        verifyFinalizeReleaseResults("0.1.1-SNAPSHOT");
    }

    @Test
    void testPatchReleaseWithSnapshot() throws IOException, GitAPIException {
        Files.writeString(gradlePropertiesFile, versionKeyValue + " = 0.0.1-SNAPSHOT");
        gitAddAndCommit(versionFile.getFileName().toString(), "update version");
        BuildResult result = runWithArguments("prepareRelease", "--releaseType", "PATCH");
        verifyPrepareReleaseResults(result, "0.0.1");

        result = runWithArguments("finalizeRelease");
        verifyFinalizeReleaseResults("0.0.2-SNAPSHOT");
    }

    @Test
    void testPatchReleaseWithoutSnapshot() throws IOException, GitAPIException {
        Files.writeString(gradlePropertiesFile, versionKeyValue + " = 0.0.1");
        gitAddAndCommit(versionFile.getFileName().toString(), "update version");
        BuildResult result = runWithArguments("prepareRelease", "--releaseType", "PATCH");
        verifyPrepareReleaseResults(result, "0.0.2");

        result = runWithArguments("finalizeRelease");
        verifyFinalizeReleaseResults("0.0.3-SNAPSHOT");
    }

    @Test
    void tesOverridingReleaseVersion() throws IOException, GitAPIException {
        BuildResult result = runWithArguments("prepareRelease", "--releaseType", "MANUAL", "--releaseVersion",
                "1.2.3-r");
        verifyPrepareReleaseResults(result, "1.2.3-r");

        result = runWithArguments("finalizeRelease");
        verifyFinalizeReleaseResults("1.2.4-SNAPSHOT");
    }

    @Test
    void tesOverridingNewVersion() throws IOException, GitAPIException {
        BuildResult result = runWithArguments("prepareRelease", "--releaseType", "PATCH");
        verifyPrepareReleaseResults(result, "0.0.1");

        result = runWithArguments("finalizeRelease", "--newVersion", "1.2.3-r");
        verifyFinalizeReleaseResults("1.2.3-r");
    }

    @Test
    void testVersionFileOverride() throws IOException, GitAPIException {
        versionFile = buildFile;

        Files.write(buildFile, Lists.newArrayList("plugins {", "  id('com.fincher.release')", "}", "", "release {",
                "    versionFile = file('build.gradle')", "}", "", "version='0.0.1'"));

        gitAddAndCommit(versionFile.getFileName().toString(), "update build.gradle to have version");

        BuildResult result = runWithArguments("prepareRelease", "--releaseType", "MAJOR");
        verifyPrepareReleaseResults(result, "1.0.0");

        result = runWithArguments("finalizeRelease");
        verifyFinalizeReleaseResults("1.0.1-SNAPSHOT");
    }

    @Test
    void testVersionKeyValueOverride() throws IOException, GitAPIException {
        versionKeyValue = "otherVersionKey";

        Files.writeString(gradlePropertiesFile, versionKeyValue + " = 0.0.1");
        gitAddAndCommit("gradle.properties", "use different version key");

        Files.write(buildFile, Lists.newArrayList("plugins {", "  id('com.fincher.release')", "}", "", "release {",
                "    versionKeyValue = 'otherVersionKey'", "}"));
        gitAddAndCommit("build.gradle", "update build.gradle to have version");

        BuildResult result = runWithArguments("prepareRelease", "--releaseType", "MAJOR");
        verifyPrepareReleaseResults(result, "1.0.0");

        result = runWithArguments("finalizeRelease");
        verifyFinalizeReleaseResults("1.0.1-SNAPSHOT");
    }

    @Test
    void testPrepareReleaseWithUncommitedChanges() throws IOException {
        Files.write(buildFile, Lists.newArrayList("plugins {", "  id('com.fincher.release')", "}", "", "release {",
                "    versionFile = file('build.gradle')", "}", "", "version='0.0.1'"));

        BuildResult result = runWithArgumentsAndFail("prepareRelease", "--releaseType", "MAJOR");
        assertTrue(result.getOutput().contains("Unable to release with uncommitted changes"));
    }

    @Test
    void testFinalizeReleaseWithUncommitedChanges() throws IOException {
        Files.write(buildFile, Lists.newArrayList("plugins {", "  id('com.fincher.release')", "}", "", "release {",
                "    versionFile = file('build.gradle')", "}", "", "version='0.0.1'"));

        BuildResult result = runWithArgumentsAndFail("finalizeRelease");
        assertTrue(result.getOutput().contains("Unable to release with uncommitted changes"));
    }

    @Test
    void testMainBranch() throws IOException, GitAPIException {
        git.checkout().setCreateBranch(true).setName("main").call();
        BuildResult result = runWithArguments("prepareRelease", "--releaseType", "MINOR");
        verifyPrepareReleaseResults(result, "0.1.0");

        result = runWithArguments("finalizeRelease");
        verifyFinalizeReleaseResults("0.1.1-SNAPSHOT");
    }

    @ParameterizedTest
    @ValueSource(strings = { "other1", "other2" })
    void testBranchOverride(String branchName) throws IOException, GitAPIException {
        git.checkout().setCreateBranch(true).setName(branchName).call();

        Files.write(buildFile, Lists.newArrayList("plugins {", "  id('com.fincher.release')", "}", "", "release {",
                "    requiredBranchRegex = '^(other1)|(other2)$'", "}"));
        gitAddAndCommit("build.gradle", "update build.gradle to have version");

        BuildResult result = runWithArguments("prepareRelease", "--releaseType", "MINOR");
        verifyPrepareReleaseResults(result, "0.1.0");

        result = runWithArguments("finalizeRelease");
        verifyFinalizeReleaseResults("0.1.1-SNAPSHOT");
    }

    @Test
    public void testInvalidBranch() throws IOException, GitAPIException {
        git.checkout().setCreateBranch(true).setName("other1").call();
        BuildResult result = runWithArgumentsAndFail("prepareRelease", "--releaseType", "MINOR");
        String version = getVersionFromFile();
        assertEquals("0.0.1-SNAPSHOT", version);

        assertTrue(
                result.getOutput().contains("Expected branch name to match pattern ^(master)|(main)$ but was other1"));
    }

    @Test
    void testInvalidReleaseType() throws IOException, GitAPIException {
        runWithArgumentsAndFail("prepareRelease", "--releaseType", "other");
        String version = getVersionFromFile();
        assertEquals("0.0.1-SNAPSHOT", version);
    }

    @Test
    void testTagPrefixOverride() throws IOException, GitAPIException {
        Files.write(buildFile, Lists.newArrayList("plugins {", "  id('com.fincher.release')", "}", "", "release {",
                "    tagPrefix = 'tagPrefix'", "}"));
        gitAddAndCommit("build.gradle", "update build.gradle to tag prefix");

        BuildResult result = runWithArguments("prepareRelease", "--releaseType", "MAJOR");
        verifyPrepareReleaseResults(result, "1.0.0", "tagPrefix");

        result = runWithArguments("finalizeRelease");
        verifyFinalizeReleaseResults("1.0.1-SNAPSHOT");
    }

    private void verifyPrepareReleaseResults(BuildResult buildResult, String expectedVersion)
            throws IOException, GitAPIException {
        verifyPrepareReleaseResults(buildResult, expectedVersion, "");
    }

    private void verifyPrepareReleaseResults(BuildResult buildResult, String expectedVersion, String tagPrefix)
            throws IOException, GitAPIException {
        String version = getVersionFromFile();
        assertEquals(expectedVersion, version);
        assertEquals(String.format("\"Set version for release to %s\"", expectedVersion),
                git.log().call().iterator().next().getFullMessage());
        AbstractReleaseTask.verifyNoUncommitedChanges(git);

        Ref latestTag = git.tagList().call().iterator().next();
        assertNotNull(latestTag);
        assertEquals("refs/tags/" + tagPrefix + expectedVersion, latestTag.getName());
    }

    private void verifyFinalizeReleaseResults(String expectedVersion) throws IOException, GitAPIException {
        String version = getVersionFromFile();
        assertEquals(expectedVersion, version);
        assertEquals(String.format("\"Set version after release to %s\"", expectedVersion),
                git.log().call().iterator().next().getFullMessage());
        AbstractReleaseTask.verifyNoUncommitedChanges(git);

    }

    private String getVersionFromFile() throws IOException {
        return VersionFile.load(versionFile, versionKeyValue).toString();
    }

    private Git initGit() throws IOException, GitAPIException {
        Git.init().setDirectory(gitRepoBareDir.toFile()).setBare(true).call();

        Git git = Git.cloneRepository().setURI(gitRepoBareDir.toUri().toString()).setDirectory(gitRepoDir.toFile())
                .call();

        return git;
    }

    private void gitAddInitialFiles(Git git) throws IOException, GitAPIException {

        Set<String> filesToAdd = Sets.newHashSet("build.gradle", "settings.gradle");
        filesToAdd.add(versionFile.getFileName().toString());

        AddCommand addCommand = git.add();
        filesToAdd.forEach(addCommand::addFilepattern);
        addCommand.call();

        git.commit().setMessage("initial commit").call();
    }

    private void gitAddAndCommit(String file, String message) throws GitAPIException {
        git.add().addFilepattern(file).call();
        git.commit().setMessage(message).call();
    }

    private static Path recursivelyDeleteDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
        return dir;
    }

    private BuildResult runWithArguments(String... arguments) {
        if (arguments.length > 0) {
            runner.withArguments(arguments);
        }

        return runner.build();
    }

    private BuildResult runWithArgumentsAndFail(String... arguments) {
        if (arguments.length > 0) {
            runner.withArguments(arguments);
        }

        return runner.buildAndFail();
    }

    private GradleRunner initRunner() {
        GradleRunner runner = GradleRunner.create();
        runner.forwardOutput();
        runner.withPluginClasspath();
        runner.withProjectDir(projectDir.toFile());
        return runner;
    }

    /** Create the given directory. If it previously exists, delete and re-create */
    private static Path createEmptyDir(Path dir) throws IOException {
        recursivelyDeleteDir(dir);
        Files.createDirectories(dir);
        return dir;
    }

}
