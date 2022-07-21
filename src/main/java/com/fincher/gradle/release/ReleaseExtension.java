package com.fincher.gradle.release;

import java.io.File;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

/** Gradle plugin extension for the ReleaseTask */
public abstract class ReleaseExtension {

    @InputFile
    abstract Property<File> getVersionFile();

    @Input
    abstract Property<String> getVersionKeyValue();

    @Input
    abstract Property<String> getRequiredBranchRegex();

    @Input
    abstract Property<String> getTagPrefix();

    @Input
    abstract Property<String> getGitRepositoryUsername();

    @Input
    abstract Property<String> getGitRepositoryPassword();

    @Input
    abstract Property<String> getGitRepositorySshPrivateKey();

    @InputFile
    abstract Property<File> getGitRepositorySshPrivateKeyFile();

    @Input
    abstract Property<String> getGitRepositorySshPassphrase();

}
