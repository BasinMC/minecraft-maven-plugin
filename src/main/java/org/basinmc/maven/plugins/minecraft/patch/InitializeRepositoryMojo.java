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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.basinmc.maven.plugins.minecraft.AbstractArtifactMojo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides a Mojo which initializes the local git repository with its respective contents.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "initialize-repository",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.GENERATE_SOURCES
)
public class InitializeRepositoryMojo extends AbstractArtifactMojo {
    private static final String ROOT_COMMIT_AUTHOR_NAME = "Basin";
    private static final String ROOT_COMMIT_AUTHOR_EMAIL = "contact@basinmc.org";

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.verifyProperties("module", "gameVersion", "mappingVersion", "sourceDirectory");

        this.getLog().info("Initializing repository at " + this.getSourceDirectory().getAbsolutePath());

        try {
            if (Files.notExists(this.getSourceDirectory().toPath()) || Files.notExists(this.getSourceDirectory().toPath().resolve(".git"))) {
                this.initializeRepository();
            } else {
                this.getLog().info("Skipping repository initialization - Cached");
            }
        } catch (ArtifactResolutionException ex) {
            throw new MojoFailureException("Failed to resolve artifact: " + ex.getMessage(), ex);
        }
    }

    /**
     * Initializes the local repository with its default state.
     */
    private void initializeRepository() throws ArtifactResolutionException, MojoFailureException {
        final Path sourceArtifact;

        {
            Artifact a = this.createArtifactWithClassifier(MINECRAFT_GROUP_ID, this.getModule(), this.getMappingVersion(), "source");
            sourceArtifact = this.findArtifact(a).orElseThrow(() -> new MojoFailureException("Could not locate artifact " + this.getArtifactCoordinateString(a)));
        }

        try {
            Files.createDirectories(this.getSourceDirectory().toPath());
            Git git = Git.init().setDirectory(this.getSourceDirectory()).call();

            try (ZipFile file = new ZipFile(sourceArtifact.toFile())) {
                Enumeration<? extends ZipEntry> enumeration = file.entries();

                while (enumeration.hasMoreElements()) {
                    ZipEntry entry = enumeration.nextElement();
                    String name = entry.getName();

                    if (!name.endsWith(".java")) {
                        continue;
                    }

                    Path outputPath = this.getSourceDirectory().toPath().resolve(name);

                    try (InputStream inputStream = file.getInputStream(entry)) {
                        try (ReadableByteChannel channel = Channels.newChannel(inputStream)) {
                            try (FileChannel outputChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                                ByteStreams.copy(channel, outputChannel);
                            }
                        }
                    }

                    git.add().addFilepattern(name).call();
                }
            }

            git.commit()
                    .setAuthor(ROOT_COMMIT_AUTHOR_NAME, ROOT_COMMIT_AUTHOR_EMAIL)
                    .setCommitter(ROOT_COMMIT_AUTHOR_NAME, ROOT_COMMIT_AUTHOR_EMAIL)
                    .setMessage("Added decompiled sources.")
                    .call();

            git.branchCreate()
                    .setName("upstream")
                    .call();

            this.getProject().addCompileSourceRoot(this.getSourceDirectory().toString());
        } catch (GitAPIException ex) {
            throw new MojoFailureException("Failed to execute Git command: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to access source artifact or write target file: " + ex.getMessage(), ex);
        }
    }
}
