package com.fincher.gradle.release;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Property;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

abstract class BaseReleaseTaskTest<T extends AbstractReleaseTask> {

    static final String INITIAL_VERSION = "0.0.1-SNAPSHOT";

    final String taskName;
    final Class<T> taskClass;

    T task;
    Project project;
    Path projectDir;
    Path versionFile;

    @Mock
    Repository repo;
    @Mock
    Git git;
    @Mock
    StatusCommand statusCmd;
    @Mock
    Status status;
    @Mock
    AddCommand addCommand;
    @Mock
    CommitCommand commitCommand;
    @Mock
    PushCommand pushCommand;
    @Mock
    TagCommand tagCommand;
    @Mock
    Property<String> versionKeyValueProperty;
    @Mock
    Property<File> versionFileProperty;

    BaseReleaseTaskTest(String taskName, Class<T> taskClass) {
        this.taskName = taskName;
        this.taskClass = taskClass;
    }

    @BeforeEach
    public void beforeEach() throws GitAPIException, IOException {
        MockitoAnnotations.openMocks(this);
        initMocks();

        project = ProjectBuilder.builder().build();
        projectDir = project.getProjectDir().toPath();
        versionFile = projectDir.resolve("gradle.properties");
        Files.writeString(versionFile, "version=" + INITIAL_VERSION);

        project.getTasks().register(taskName, taskClass);
        Task t = project.getTasks().findByName(taskName);
        assertNotNull(t);

        task = taskClass.cast(t);
        task.setJGitRepoFactory(() -> repo);
        task.setGitFactory((__) -> git);
    }

    void initMocks() throws GitAPIException, IOException {
        when(git.status()).thenReturn(statusCmd);
        when(statusCmd.call()).thenReturn(status);
        when(status.hasUncommittedChanges()).thenReturn(false);

        when(repo.getBranch()).thenReturn("master");

        when(git.add()).thenReturn(addCommand);
        when(addCommand.addFilepattern(anyString())).thenReturn(addCommand);

        when(git.commit()).thenReturn(commitCommand);
        when(commitCommand.setMessage(anyString())).thenReturn(commitCommand);

        when(git.push()).thenReturn(pushCommand);
        when(pushCommand.setPushTags()).thenReturn(pushCommand);

        when(git.tag()).thenReturn(tagCommand);
        when(tagCommand.setMessage(anyString())).thenReturn(tagCommand);
        when(tagCommand.setName(anyString())).thenReturn(tagCommand);
        when(tagCommand.setAnnotated(anyBoolean())).thenReturn(tagCommand);

        when(versionKeyValueProperty.getOrElse(anyString())).then(returnsFirstArg());
        when(versionFileProperty.getOrElse(any(File.class))).then(returnsFirstArg());
    }

}
