package com.fincher.gradle.release;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/**
 * Parent for other release tasks
 * 
 * @author Brian Fincher
 *
 */
public abstract class AbstractReleaseTask extends DefaultTask {

    /** For unit testing purposes. Creates the JGit repository */
    @FunctionalInterface
    static interface JGitRepoFactory {
        Repository initGitRepo() throws IOException;
    }

    /** For unit testing purposes. Creates the JGit object */
    @FunctionalInterface
    static interface JGitFactory {
        Git initGit(Repository repo);
    }

    protected Repository repo;
    protected Git git;
    protected VersionFile version;
    protected String relativeVersionFile;
    protected java.util.Optional<CredentialsProvider> credentialsProvider;
    private JGitRepoFactory repoFactory;
    private JGitFactory gitFactory;

    AbstractReleaseTask() {
        repoFactory = this::initGitRepo;
        gitFactory = Git::new;
    }

    /**
     * The file that contains the version property. Defaults to gradle.properties
     * 
     * @return The file that contains the version property. Defaults to gradle.properties
     */
    @InputFile
    @Optional
    public abstract Property<File> getVersionFile();

    /**
     * The key in the version key value pair. For example, <code>"myProectVersion = 1.0.0"</code> would have a
     * versionKeyValue of <code>myProectVersion</code>
     * 
     * @return The key in the version key value pair. Defaults to "version"
     */
    @Input
    @Optional
    public abstract Property<String> getVersionKeyValue();

    /**
     * A regular expression that is compared against the branch on which we are trying to release. Default is
     * <code>^(master)|(main)$</code>. By default, releases can only happen on the master or main branch
     * 
     * @return A regular expression that is compared against the branch on which we are trying to release.
     */
    @Input
    @Optional
    public abstract Property<String> getRequiredBranchRegex();

    /**
     * The main task action
     * 
     * @throws GitAPIException If an exception occurs trying to initialize Git
     * @throws IOException If an exception occurs trying to initialize Git or reading the version file
     */
    @TaskAction
    public void releaseTaskAction() throws GitAPIException, IOException {

        Project project = getProject();
        Path projectDir = project.getProjectDir().toPath();

        version = VersionFile.load(getProject(), getVersionFile(), getVersionKeyValue());

        relativeVersionFile = projectDir.relativize(version.getFile()).toString();

        repo = repoFactory.initGitRepo();
        git = gitFactory.initGit(repo);

        verifyNoUncommitedChanges();

        String branch = repo.getBranch();
        String branchPattern = getRequiredBranchRegex().getOrElse("^(master)|(main)$");
        if (!Pattern.compile(branchPattern).matcher(branch).matches()) {
            String errorMsg = String.format("Expected branch name to match pattern %s but was %s", branchPattern,
                    branch);
            throw new IllegalStateException(errorMsg);
        }
    }

    // For testing purposes
    protected void setJGitRepoFactory(JGitRepoFactory factory) {
        repoFactory = factory;
    }

    // For testing purposes
    protected void setGitFactory(JGitFactory factory) {
        gitFactory = factory;
    }

    protected static String replaceGroup(String source, Matcher matcher, String group, String replacement) {
        return new StringBuilder(source).replace(matcher.start(group), matcher.end(group), replacement).toString();
    }

    private Repository initGitRepo() throws IOException {
        Path dirToSearch = getProject().getProjectDir().toPath();
        Path testDir = dirToSearch.resolve(".git");
        Path gitDir = null;

        for (int i = 0; i < 5; i++) {
            if (Files.exists(testDir) && Files.isDirectory(testDir)) {
                gitDir = testDir;
                break;
            }
        }

        if (gitDir == null) {
            getProject().getLogger().error("Unable to find .git directory");
            throw new GradleException("Unable to find .git directory");
        }

        return new FileRepositoryBuilder().setGitDir(gitDir.toFile()).build();
    }

    protected void overrideVersion(String versionOverride) {
        Pattern p = Pattern.compile(VersionFile.VERSION_PATTERN_STR);
        Matcher m = p.matcher(versionOverride);
        if (!m.find()) {
            String errorMsg = String.format("The version of %s does not match the pattern %s", versionOverride,
                    VersionFile.VERSION_PATTERN_STR);
            throw new IllegalArgumentException(errorMsg);
        }

        version.replaceMajor(m.group(VersionFile.MAJOR_GROUP));
        version.replaceMinor(m.group(VersionFile.MINOR_GROUP));
        version.replacePatch(m.group(VersionFile.PATCH_GROUP));
        version.replaceSuffix(m.group(VersionFile.SUFFIX_GROUP));
    }

    protected void verifyNoUncommitedChanges() throws GitAPIException {
        verifyNoUncommitedChanges(git);
    }

    protected static void verifyNoUncommitedChanges(Git git) throws GitAPIException {
        Status status = git.status().call();
        if (status.hasUncommittedChanges()) {
            throw new IllegalStateException("Unable to release with uncommitted changes");
        }
    }

}
