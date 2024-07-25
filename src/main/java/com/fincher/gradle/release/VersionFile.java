package com.fincher.gradle.release;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

class VersionFile {

    private static final String VERSION_PREFIX_GROUP = "versionPrefix";
    private static final String VERSION_GROUP = "version";
    static final String MAJOR_GROUP = "major";
    static final String MINOR_GROUP = "minor";
    static final String PATCH_GROUP = "patch";
    static final String SUFFIX_GROUP = "suffix";

    static final String VERSION_PATTERN_STR = String.format(
            "(?<%s>[\'\"]?(?<%s>\\d+)\\.(?<%s>\\d+)\\.(?<%s>\\d+)(?<%s>[a-zA-Z0-9_-]*)[\'\"]?)", VERSION_GROUP,
            MAJOR_GROUP, MINOR_GROUP, PATCH_GROUP, SUFFIX_GROUP);

    private final Path file;
    private final Pattern pattern;
    private final List<String> fileContent;
    private final int fileContentIndex;
    private String major;
    private String minor;
    private String patch;
    private String suffix;

    private VersionFile(Path file, Pattern pattern, List<String> fileContent, int fileContentIndex,
            SemanticVersion version, String suffix) {
        this.file = file;
        this.pattern = pattern;
        this.fileContent = fileContent;
        this.fileContentIndex = fileContentIndex;
        this.major = version.major;
        this.minor = version.minor;
        this.patch = version.patch;
        this.suffix = suffix;
    }

    static VersionFile load(Project project, Property<File> fileProperty, Property<String> versionKeyValue)
            throws IOException {
        Path file = fileProperty.getOrElse(new File(project.getProjectDir(), "gradle.properties")).toPath();
        return load(file, versionKeyValue.getOrElse(VERSION_GROUP));
    }

    static VersionFile load(Path file, String versionKeyValue) throws IOException {
        List<String> fileContent = Files.readAllLines(file);

        String versionLinePatternStr = String.format("(?<%s>\\s*%s\\s*=\\s*)%s", VERSION_PREFIX_GROUP, versionKeyValue,
                VERSION_PATTERN_STR);
        Pattern pattern = Pattern.compile(versionLinePatternStr);

        int lineIndex = -1;
        String major = null;
        String minor = null;
        String patch = null;
        String suffix = null;

        for (int i = 0; i < fileContent.size(); i++) {
            Matcher m = pattern.matcher(fileContent.get(i));
            if (m.find()) {
                if (lineIndex != -1) {
                    throw new IllegalStateException("Multiple lines found matching the version pattern");
                }

                lineIndex = i;
                major = m.group(MAJOR_GROUP);
                minor = m.group(MINOR_GROUP);
                patch = m.group(PATCH_GROUP);
                suffix = m.group(SUFFIX_GROUP);
            }
        }

        if (lineIndex == -1) {
            throw new IllegalStateException("Unable to parse the version");
        }

        return new VersionFile(file, pattern, fileContent, lineIndex, new SemanticVersion(major, minor, patch), suffix);
    }

    String getMajor() {
        return major;
    }

    String getMinor() {
        return minor;
    }

    String getPatch() {
        return patch;
    }

    String getSuffix() {
        return suffix;
    }

    Path getFile() {
        return file;
    }

    void save() throws IOException {
        if (file == null) {
            throw new IllegalStateException("Cannot save when not loaded from a file");
        }
        Files.write(file, fileContent);
    }

    void replaceMajor(String newValue) {
        replace(MAJOR_GROUP, newValue);
        major = newValue;
    }

    void replaceMinor(String newValue) {
        replace(MINOR_GROUP, newValue);
        minor = newValue;
    }

    void replacePatch(String newValue) {
        replace(PATCH_GROUP, newValue);
        patch = newValue;
    }

    void replaceSuffix(String newValue) {
        replace(SUFFIX_GROUP, newValue);
        suffix = newValue;
    }

    @Override
    public String toString() {
        return String.format("%s.%s.%s%s", major, minor, patch, suffix);
    }

    private void replace(String group, String newValue) {
        String line = fileContent.get(fileContentIndex);
        Matcher m = pattern.matcher(line);
        m.find();

        line = new StringBuilder(line).replace(m.start(group), m.end(group), newValue).toString();
        fileContent.set(fileContentIndex, line);
    }

    private static class SemanticVersion {
        private final String major;
        private final String minor;
        private final String patch;

        SemanticVersion(String major, String minor, String patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

    }
}
