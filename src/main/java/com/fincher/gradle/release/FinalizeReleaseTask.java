package com.fincher.gradle.release;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.util.FS;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.options.Option;

import com.google.common.base.Preconditions;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

public abstract class FinalizeReleaseTask extends AbstractReleaseTask {

    private String newVersionOverride = null;

    /**
     * If this repository is HTTPS and authentication is required, this property contains the authentication user name
     * 
     * @return the authentication user name
     */
    @Input
    @Optional
    public abstract Property<String> getGitRepositoryUsername();

    /**
     * If this repository is HTTPS and authentication is required, this property contains the authentication password
     * 
     * @return the authentication password
     */
    @Input
    @Optional
    public abstract Property<String> getGitRepositoryPassword();

    /**
     * If this repository is SSH, this property contains the SSH private key
     * 
     * @return the SSH private key
     */
    @Input
    @Optional
    public abstract Property<String> getGitRepositorySshPrivateKey();

    /**
     * If this repository is SSH, this property contains the file containing the SSH private key
     * 
     * @return the file containing the SSH private key
     */
    @InputFile
    @Optional
    public abstract Property<File> getGitRepositorySshPrivateKeyFile();

    /**
     * If this repository is SSH and the SSH private key contains the pass phrase.
     * 
     * @return The SSH private key pass phrase
     */
    @Input
    @Optional
    public abstract Property<String> getGitRepositorySshPassphrase();

    @Option(option = "newVersion", description = "Sets the new release value.   "
            + "If the prepareRelease set the release version to 1.0.1, "
            + "the default new release value would be 1.0.1-SNAPSHOT")
    void setNewVersion(String newVersion) {
        newVersionOverride = newVersion;
    }

    @Override
    public void releaseTaskAction() throws IOException, GitAPIException {
        super.releaseTaskAction();
        validateRepositoryAuthenticationParams();

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

        StoredConfig config = repo.getConfig();
        Objects.requireNonNull(config, "config is null");
        String branch = repo.getBranch();
        config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, "remote", "origin");
        config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, "merge", "refs/heads/" + branch);
        config.save();

        executePushCommand(git.push());
        executePushCommand(git.push().setPushTags().setForce(true));
    }

    protected Iterable<PushResult> executePushCommand(PushCommand command) throws GitAPIException, IOException {
        Iterable<PushResult> pushResult = executeTransportCommand(command);
        Logger logger = getLogger();
        String branch = repo.getBranch();
        pushResult.forEach(result -> logger.lifecycle("Pushed {} {} branch: {} updates: {}",
                result.getMessages(),
                result.getURI(),
                branch,
                result.getRemoteUpdates()));
        return pushResult;
    }

    @SuppressWarnings("rawtypes")
    protected <T> T executeTransportCommand(TransportCommand<? extends GitCommand, T> command)
            throws GitAPIException {

        if (getGitRepositorySshPrivateKey().isPresent() || getGitRepositorySshPrivateKeyFile().isPresent()) {
            return executeTransportCommandSsh(command);
        }

        if (getGitRepositoryUsername().isPresent()) {
            command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(getGitRepositoryUsername().get(),
                    getGitRepositoryPassword().get()));
        }

        return command.call();
    }

    @SuppressWarnings("rawtypes")
    protected <T> T executeTransportCommandSsh(TransportCommand<? extends GitCommand, T> command)
            throws GitAPIException {
        CustomSshSessionFactory sshSessionFactory = new CustomSshSessionFactory();

        command.setTransportConfigCallback(transport -> {
            SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(sshSessionFactory);
        });

        JSch.setConfig("StrictHostKeyChecking", "no");

        return command.call();
    }

    private void validateRepositoryAuthenticationParams() {
        boolean isUsernameSet = getGitRepositoryUsername().isPresent();
        boolean isPasswordSet = getGitRepositoryPassword().isPresent();
        boolean isPrivateKeySet = getGitRepositorySshPrivateKey().isPresent();
        boolean isPrivateKeyFileSet = getGitRepositorySshPrivateKeyFile().isPresent();
        boolean isPrivateKeyPassphraseSet = getGitRepositorySshPassphrase().isPresent();

        if (isUsernameSet) {
            Preconditions.checkState(isPasswordSet,
                    "Git repository username is set but not Git password");
            final String duplicateErrorMsg = "Both username/password and SSH authentication parameters cannot be set";

            Preconditions.checkState(!isPrivateKeySet, duplicateErrorMsg);
            Preconditions.checkState(!isPrivateKeyFileSet, duplicateErrorMsg);
            Preconditions.checkState(!isPrivateKeyPassphraseSet, duplicateErrorMsg);
            return;
        } else {
            Preconditions.checkState(!isPasswordSet,
                    "Git repository password is set but not Git username");
        }

        if (isPrivateKeyPassphraseSet) {
            Preconditions.checkState(
                    getGitRepositorySshPrivateKey().isPresent() || getGitRepositorySshPrivateKeyFile().isPresent(),
                    "SSH passphrase is set but not the SSH private key");
        }

        if (isPrivateKeySet) {
            Preconditions.checkState(!isUsernameSet,
                    "Both SSH private Key and username cannot be set");
            Preconditions.checkState(!isPasswordSet,
                    "Both SSH private Key and password cannot be set");
            Preconditions.checkState(!isPrivateKeyFileSet,
                    "Both SSH private Key and SSH private key file parameters cannot be set");
            return;
        }

        if (isPrivateKeyFileSet) {
            Preconditions.checkState(!isUsernameSet,
                    "Both SSH private Key and username cannot be set");
            Preconditions.checkState(!isPasswordSet,
                    "Both SSH private Key and password cannot be set");
            Preconditions.checkState(!isPrivateKeySet,
                    "Both SSH private Key and SSH private key file parameters cannot be set");
        }
    }

    private class CustomSshSessionFactory extends JschConfigSessionFactory {

        private final String privateKeyFile;
        private final byte[] privateKey;
        private final String passphrase;

        CustomSshSessionFactory() {
            passphrase = getGitRepositorySshPassphrase().getOrNull();

            if (getGitRepositorySshPrivateKeyFile().isPresent()) {
                privateKeyFile = getGitRepositorySshPrivateKeyFile().get().toPath().toString();
                privateKey = null;
            } else {
                privateKey = getGitRepositorySshPrivateKey().get().getBytes();
                privateKeyFile = null;
            }
        }

        @Override
        protected JSch createDefaultJSch(FS fs) throws JSchException {
            JSch defaultJSch = super.createDefaultJSch(fs);
            try {
                defaultJSch.setKnownHosts(getSshKey());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new JSchException(ie.getMessage(), ie);
            } catch (IOException | GitAPIException e) {
                throw new JSchException(e.getMessage(), e);
            }

            if (privateKeyFile != null) {
                if (passphrase == null) {
                    defaultJSch.addIdentity(privateKeyFile);
                } else {
                    defaultJSch.addIdentity(privateKeyFile, passphrase);
                }
            } else {
                if (passphrase == null) {
                    defaultJSch.addIdentity("key", privateKey, (byte[]) null, null);
                } else {
                    defaultJSch.addIdentity("key", privateKey, (byte[]) null, passphrase.getBytes());
                }
            }

            return defaultJSch;
        }

        private InputStream getSshKey() throws IOException, GitAPIException, InterruptedException {
            java.util.Optional<URIish> uri =
                    git.remoteList().call().stream()
                            .filter(remote -> remote.getName().equals("origin"))
                            .map(RemoteConfig::getURIs)
                            .map(list -> list.iterator().next())
                            .findFirst();

            if (uri.isPresent()) {
                uri.get().getHost();
                final String[] keyscanCommand = { "ssh-keyscan", uri.get().getHost() };
                final Process p = new ProcessBuilder(keyscanCommand).start();
                p.waitFor();
                if (p.exitValue() == 0) {
                    return p.getInputStream();
                } else {
                    throw new AssertionError("Bad return code from ssh-keyscan");
                }
            } else {
                throw new AssertionError("Unable to find remote host");
            }
        }
    }
}
