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
package org.basinmc.maven.plugins.minecraft.patch;

import com.google.common.io.ByteStreams;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProjectHelper;
import org.basinmc.maven.plugins.minecraft.AbstractMappingMojo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides a Mojo capable of extracting the module resources and adding them to the build.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "extract-resources",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES
)
public class ExtractResourcesMojo extends AbstractMappingMojo {

    // <editor-fold desc="Maven Components">
    @Component
    private MavenProjectHelper projectHelper;
    // </editor-fold>

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.verifyProperties("module", "gameVersion", "mappingVersion", "resourceDirectory");

        this.getLog().info("Extracting Resources");
        final Path sourceArtifact;

        try {
            {
                Artifact a = this.createArtifactWithClassifier(MINECRAFT_GROUP_ID, this.getModule(), this.getMappedArtifactVersion(), "source");
                sourceArtifact = this.findArtifact(a).orElseThrow(() -> new MojoFailureException("Could not locate artifact " + this.getArtifactCoordinateString(a)));
            }

            final List<String> resources = new ArrayList<>();

            try (ZipFile file = new ZipFile(sourceArtifact.toFile())) {
                Enumeration<? extends ZipEntry> enumeration = file.entries();

                while (enumeration.hasMoreElements()) {
                    ZipEntry entry = enumeration.nextElement();
                    String name = entry.getName();

                    if (name.endsWith(".java") || entry.isDirectory()) {
                        continue;
                    }

                    if (this.getExcludedResources() != null && this.getExcludedResources().contains(name)) {
                        this.getLog().info("Skipping resource " + name + " - Excluded by build configuration");
                        continue;
                    }

                    Path outputPath = this.getResourceDirectory().toPath().resolve(name);
                    resources.add(name);

                    if (!Files.isDirectory(outputPath.getParent())) {
                        Files.createDirectories(outputPath.getParent());
                    }

                    if (Files.exists(outputPath)) {
                        if (!entry.getLastModifiedTime().toInstant().isAfter(Files.getLastModifiedTime(outputPath).toInstant())) {
                            this.getLog().info("Skipping resource " + name + " - Local file is newer or of equal age");
                            continue;
                        }
                    }

                    try (InputStream inputStream = file.getInputStream(entry)) {
                        try (ReadableByteChannel channel = Channels.newChannel(inputStream)) {
                            try (FileChannel outputChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                                ByteStreams.copy(channel, outputChannel);
                            }
                        }
                    }
                }
            }

            this.projectHelper.addResource(this.getProject(), this.getResourceDirectory().toString(), resources, Collections.emptyList());
        } catch (ArtifactResolutionException ex) {
            throw new MojoFailureException("Cannot resolve artifact: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Cannot not read or write source artifact or target file: " + ex.getMessage(), ex);
        }
    }
}
