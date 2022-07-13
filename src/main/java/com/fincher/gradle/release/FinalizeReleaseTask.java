package com.fincher.gradle.release;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.api.tasks.options.Option;

public abstract class FinalizeReleaseTask extends AbstractReleaseTask {

    private String newVersionOverride = null;

    @Option(option = "newVersion", description = "Sets the new release value.   "
            + "If the prepareRelease set the release version to 1.0.1, "
            + "the default new release value would be 1.0.1-SNAPSHOT")
    void setNewVersion(String newVersion) {
        newVersionOverride = newVersion;
    }

    @Override
    public void releaseTaskAction() throws IOException, GitAPIException {
        super.releaseTaskAction();

        if (newVersionOverride == null) {
            version.replacePatch(String.valueOf(Integer.parseInt(version.getPatch()) + 1));
            version.replaceSuffix("-SNAPSHOT");
        } else {
            overrideVersion(newVersionOverride);
        }

        version.save();

        git.add().addFilepattern(relativeVersionFile).call();

        String newVersion = version.toString();
        git.commit().setMessage(String.format("\"Set version after release to %s\"", newVersion)).call();
        git.push().setPushTags().call();
    }

}
