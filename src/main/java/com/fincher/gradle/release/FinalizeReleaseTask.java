package com.fincher.gradle.release;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.util.FS;
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
        Iterable<PushResult> result = executeTransportCommand(git.push().setPushTags());

        result.forEach(r -> System.out.println(r.getMessages()));

    }

    @SuppressWarnings("rawtypes")
    protected <T> T executeTransportCommand(TransportCommand<? extends GitCommand, T> command)
            throws GitAPIException, IOException {

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
            throws GitAPIException, IOException {

        final String privateKeyFile;
        final byte[] privateKey;
        String passphrase = getGitRepositorySshPassphrase().getOrNull();

        if (getGitRepositorySshPrivateKeyFile().isPresent()) {
            privateKeyFile = getGitRepositorySshPrivateKeyFile().get().toPath().toString();
            privateKey = null;
        } else {
            privateKey = getGitRepositorySshPrivateKey().get().getBytes();
            privateKeyFile = null;
        }

        SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch(fs);

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
        };

        command.setTransportConfigCallback(new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                SshTransport sshTransport = (SshTransport) transport;
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
        });

        return command.call();
    }

    private void validateRepositoryAuthenticationParams() throws IOException {
        boolean isUsernameSet = getGitRepositoryUsername().isPresent();
        boolean isPasswordSet = getGitRepositoryPassword().isPresent();
        boolean isPrivateKeySet = getGitRepositorySshPrivateKey().isPresent();
        boolean isPrivateKeyFileSet = getGitRepositorySshPrivateKeyFile().isPresent();
        boolean isPrivateKeyPassphraseSet = getGitRepositorySshPassphrase().isPresent();

        if (isUsernameSet) {
            Preconditions.checkState(isPasswordSet,
                    "Git repository username is set but not Git password");
            Preconditions.checkState(!isPrivateKeySet,
                    "Both username/password and SSH authentication parameters cannot be set");
            Preconditions.checkState(!isPrivateKeyFileSet,
                    "Both username/password and SSH authentication parameters cannot be set");
            Preconditions.checkState(!isPrivateKeyPassphraseSet,
                    "Both username/password and SSH authentication parameters cannot be set");
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

}
