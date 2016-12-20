/*
 * Copyright 2016 Johannes Donath <johannesd@torchmind.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.basinmc.maven.plugins.minecraft;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.WillNotClose;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides a basic abstract Maven Mojo implementation with utility methods for interacting with
 * Maven artifacts.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Immutable
@ThreadSafe
public abstract class AbstractArtifactMojo extends AbstractMinecraftMojo {
    protected static final String BASE_GROUP_ID = "org.basinmc";

    protected static final String MINECRAFT_GROUP_ID = BASE_GROUP_ID + ".minecraft";
    protected static final String SRG_ARTIFACT_ID = "mappings-srg";
    protected static final String MCP_ARTIFACT_ID = "mappings-mcp";
    protected static final String MCP_LIVE_VERSION = "0.0.0-SNAPSHOT";
    protected static final String VANILLA_CLASSIFIER = "vanilla";
    protected static final String MAPPED_CLASSIFIER = "mapped";
    protected static final String SOURCE_CLASSIFIER = "source";

    // <editor-fold desc="Maven Components">
    @Component
    private ArtifactFactory artifactFactory;
    @Component
    private ArtifactInstaller artifactInstaller;
    @Component
    private ArtifactResolver artifactResolver;
    // </editor-fold>

    // <editor-fold desc="Component Getters">
    @Nonnull
    protected ArtifactFactory getArtifactFactory() {
        return this.artifactFactory;
    }

    @Nonnull
    protected ArtifactInstaller getArtifactInstaller() {
        return this.artifactInstaller;
    }

    @Nonnull
    protected ArtifactResolver getArtifactResolver() {
        return this.artifactResolver;
    }
    // </editor-fold>

    /**
     * Creates an artifact with the supplied coordinates.
     */
    @Nonnull
    protected Artifact createArtifact(@Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type, @Nonnull String classifier) {
        return this.getArtifactFactory().createArtifactWithClassifier(groupId, artifactId, version, type, classifier);
    }

    /**
     * Creates an artifact with the supplied coordinates.
     */
    @Nonnull
    protected Artifact createArtifact(@Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String type) {
        return this.getArtifactFactory().createBuildArtifact(groupId, artifactId, version, type);
    }

    /**
     * Creates an artifact with the supplied coordinate and its type set to "jar".
     */
    protected Artifact createArtifactWithClassifier(@Nonnull String groupId, @Nonnull String artifactId, @Nonnull String version, @Nonnull String classifier) {
        return this.createArtifact(groupId, artifactId, version, "jar", classifier);
    }

    /**
     * Fetches any resource from a remote HTTP server and stores it in a specified file.
     */
    protected void fetch(@Nonnull String url, @Nonnull Path target) throws IOException {
        this.fetch(new URL(url), target);
    }

    /**
     * Fetches any resource from a remote HTTP server and stores it in a specified file.
     */
    protected void fetch(@Nonnull URL url, @Nonnull Path target) throws IOException {
        try {
            this.fetch(url.toURI(), target);
        } catch (URISyntaxException ex) {
            throw new IOException("Invalid resource URI: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetches any resources from a remote HTTP server and stores it in a specified file.
     */
    protected void fetch(@Nonnull URI uri, @Nonnull Path target) throws IOException {
        this.fetch(uri, FileChannel.open(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
    }

    /**
     * Fetches any resource from a remote HTTP server and writes it to a supplied output stream.
     */
    protected void fetch(@Nonnull URI uri, @Nonnull @WillNotClose OutputStream outputStream) throws IOException {
        this.fetch(uri, Channels.newChannel(outputStream));
    }

    /**
     * Fetches any resource from a remote HTTP server and writes it to a supplied channel.
     */
    protected void fetch(@Nonnull URI uri, @Nonnull @WillNotClose WritableByteChannel outputChannel) throws IOException {
        HttpClient client = HttpClients.createMinimal();

        HttpGet request = new HttpGet(uri);
        HttpResponse response = client.execute(request);

        StatusLine line = response.getStatusLine();

        if (line.getStatusCode() != 200) {
            throw new IOException("Unexpected status code: " + line.getStatusCode() + " - " + line.getReasonPhrase());
        }

        try (InputStream inputStream = response.getEntity().getContent()) {
            try (ReadableByteChannel inputChannel = Channels.newChannel(inputStream)) {
                ByteBuffer buf = ByteBuffer.allocateDirect(128);

                while (inputChannel.read(buf) != 0) {
                    buf.flip();
                    {
                        outputChannel.write(buf);
                    }
                    buf.reset();
                }
            }
        }
    }

    /**
     * Attempts to locate an artifact within the local Maven repository.
     */
    @Nonnull
    protected Optional<Path> findArtifact(Artifact artifact) throws ArtifactResolutionException {
        try {
            this.getArtifactResolver().resolve(artifact, Collections.emptyList(), this.getSession().getLocalRepository());
            return Optional.of(artifact.getFile().toPath());
        } catch (ArtifactNotFoundException ex) {
            return Optional.empty();
        }
    }

    /**
     * Retrieves a human readable set of artifact coordinates.
     */
    @Nonnull
    protected String getArtifactCoordinateString(@Nonnull Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() + ":" + artifact.getType() + (artifact.hasClassifier() ? ":" + artifact.getClassifier() : "");
    }

    /**
     * Installs a passed artifact into the local repository.
     */
    protected void installArtifact(@Nonnull Artifact artifact, @Nonnull Path modelPath, @Nonnull Path artifactPath) throws ArtifactInstallationException {
        this.getLog().debug("Installing artifact " + this.getArtifactCoordinateString(artifact));

        ArtifactMetadata metadata = new ProjectArtifactMetadata(artifact, modelPath.toFile());
        artifact.addMetadata(metadata);

        this.getArtifactInstaller().install(artifactPath.toFile(), artifact, this.getSession().getLocalRepository());
    }
}
