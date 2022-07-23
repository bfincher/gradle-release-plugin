package com.fincher.gradle.release;

import java.util.function.Supplier;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

public class ReleasePlugin implements Plugin<Project> {

    private <T> void setTaskPropertyFromExtension(Property<T> source, Supplier<Property<T>> dest) {
        if (source.isPresent()) {
            dest.get().set(source.get());
        }
    }

    @Override
    public void apply(Project project) {

        ReleaseExtension extension = project.getExtensions().create("release", ReleaseExtension.class);

        project.getTasks().register("prepareRelease", PrepareReleaseTask.class, task -> {
            setTaskPropertyFromExtension(extension.getVersionFile(), task::getVersionFile);
            setTaskPropertyFromExtension(extension.getVersionKeyValue(), task::getVersionKeyValue);
            setTaskPropertyFromExtension(extension.getRequiredBranchRegex(), task::getRequiredBranchRegex);
            setTaskPropertyFromExtension(extension.getTagPrefix(), task::getTagPrefix);
            task.setGroup("Publishing");
        });

        project.getTasks().register("finalizeRelease", FinalizeReleaseTask.class, task -> {
            setTaskPropertyFromExtension(extension.getVersionFile(), task::getVersionFile);
            setTaskPropertyFromExtension(extension.getVersionKeyValue(), task::getVersionKeyValue);
            setTaskPropertyFromExtension(extension.getRequiredBranchRegex(), task::getRequiredBranchRegex);
            setTaskPropertyFromExtension(extension.getGitRepositoryUsername(), task::getGitRepositoryUsername);
            setTaskPropertyFromExtension(extension.getGitRepositoryPassword(), task::getGitRepositoryPassword);
            setTaskPropertyFromExtension(extension.getGitRepositorySshPrivateKey(),
                    task::getGitRepositorySshPrivateKey);
            setTaskPropertyFromExtension(extension.getGitRepositorySshPrivateKeyFile(),
                    task::getGitRepositorySshPrivateKeyFile);
            setTaskPropertyFromExtension(extension.getGitRepositorySshPassphrase(),
                    task::getGitRepositorySshPassphrase);
            task.setGroup("Publishing");
        });
    }
}
