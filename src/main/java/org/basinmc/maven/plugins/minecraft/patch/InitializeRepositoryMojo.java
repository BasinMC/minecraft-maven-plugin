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
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.basinmc.maven.plugins.minecraft.AbstractMappingMojo;
import org.basinmc.maven.plugins.minecraft.access.AccessTransformationMap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.source.FieldHolderSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodHolderSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.VisibilityScopedSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nonnull;

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
public class InitializeRepositoryMojo extends AbstractMappingMojo {
    private static final String ROOT_COMMIT_AUTHOR_NAME = "Basin";
    private static final String ROOT_COMMIT_AUTHOR_EMAIL = "contact@basinmc.org";

    /**
     * Applies access transformations to a parsed type and all of its nested members.
     *
     * TODO: Add support for inheritance to reduce the size of AT configs.
     */
    @SuppressWarnings("unchecked")
    private void applyAccessTransformation(@Nonnull AccessTransformationMap transformationMap, @Nonnull JavaType classSource) {
        this.getLog().info("Applying access transformations to " + classSource.getQualifiedName());

        transformationMap.getTypeMappings(classSource.getQualifiedName()).ifPresent((t) -> {
            if (classSource instanceof VisibilityScopedSource) {
                t.getVisibility().ifPresent(((VisibilityScopedSource) classSource)::setVisibility);
            }

            if (classSource instanceof FieldHolderSource) {
                ((List<FieldSource>) ((FieldHolderSource) classSource).getFields()).forEach((f) -> t.getFieldVisibility(f.getName()).ifPresent(f::setVisibility));
            }

            if (classSource instanceof MethodHolderSource) {
                ((List<MethodSource>) ((MethodHolderSource) classSource).getMethods()).forEach((m) -> t.getMethodVisibility(m.getName()).ifPresent(m::setVisibility));
            }

            ((List<JavaType>) classSource.getNestedClasses()).forEach((c) -> this.applyAccessTransformation(transformationMap, c));
        });
    }

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

            this.getProject().addCompileSourceRoot(this.getSourceDirectory().toString());
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
            Artifact a = this.createArtifactWithClassifier(MINECRAFT_GROUP_ID, this.getModule(), this.getMappedArtifactVersion(), "source");
            sourceArtifact = this.findArtifact(a).orElseThrow(() -> new MojoFailureException("Could not locate artifact " + this.getArtifactCoordinateString(a)));
        }

        try {
            Files.createDirectories(this.getSourceDirectory().toPath());
            Git git = Git.init().setDirectory(this.getSourceDirectory()).call();

            AccessTransformationMap transformationMap = null;
            Formatter formatter = null;

            if (this.getAccessTransformation() != null) {
                transformationMap = AccessTransformationMap.read(this.getAccessTransformation().toPath());
                formatter = new Formatter();
            }

            try (ZipFile file = new ZipFile(sourceArtifact.toFile())) {
                Enumeration<? extends ZipEntry> enumeration = file.entries();

                while (enumeration.hasMoreElements()) {
                    ZipEntry entry = enumeration.nextElement();
                    String name = entry.getName();

                    if (!name.endsWith(".java")) {
                        continue;
                    }

                    Path outputPath = this.getSourceDirectory().toPath().resolve(name);

                    if (!Files.isDirectory(outputPath.getParent())) {
                        Files.createDirectories(outputPath.getParent());
                    }

                    try (InputStream inputStream = file.getInputStream(entry)) {
                        try (FileChannel outputChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                            if (transformationMap != null && transformationMap.getTypeMappings(name).isPresent()) {
                                JavaClassSource classSource = Roaster.parse(JavaClassSource.class, inputStream);
                                this.applyAccessTransformation(transformationMap, classSource);
                                outputChannel.write(ByteBuffer.wrap(formatter.formatSource(classSource.toString()).getBytes(StandardCharsets.UTF_8)));
                            } else {
                                try (ReadableByteChannel channel = Channels.newChannel(inputStream)) {
                                    ByteStreams.copy(channel, outputChannel);
                                }
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
        } catch (FormatterException ex) {
            throw new MojoFailureException("Failed to format one or more source files: " + ex.getMessage(), ex);
        } catch (GitAPIException ex) {
            throw new MojoFailureException("Failed to execute Git command: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to access source artifact or write target file: " + ex.getMessage(), ex);
        }
    }
}
