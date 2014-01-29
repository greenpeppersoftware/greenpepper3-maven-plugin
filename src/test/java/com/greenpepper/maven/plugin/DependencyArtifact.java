package com.greenpepper.maven.plugin;

import org.apache.maven.plugin.testing.stubs.ArtifactStub;

import java.io.File;

public class DependencyArtifact extends ArtifactStub {

    private String artifactId;
    private File file;

    public DependencyArtifact(String artifactId, File file) {
        this.artifactId = artifactId;
        this.file = file;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getType() {
        return "jar";
    }

    public File getFile() {
        return file;
    }
}