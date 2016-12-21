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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides a base Maven Mojo implementation in order to share configuration properties and
 * component references.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Immutable
@ThreadSafe
public abstract class AbstractMinecraftMojo extends AbstractMojo {

    // <editor-fold desc="Maven Components">
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;
    // </editor-fold>

    // <editor-fold desc="Configuration Properties">
    @Parameter(required = true)
    private String gameVersion;
    @Parameter(required = true)
    private String mappingVersion;
    @Parameter(required = true)
    private String module;
    @Parameter(defaultValue = "${project.basedir}/src/minecraft/patch", required = true)
    private File patchDirectory;
    @Parameter(defaultValue = "${project.basedir}/src/minecraft/java", required = true)
    private File sourceDirectory;
    @Parameter(defaultValue = "${project.basedir}/src/minecraft/resource", required = true)
    private File resourceDirectory;
    // </editor-fold>

    // <editor-fold desc="Component Getters">
    @Nonnull
    protected MavenProject getProject() {
        return this.project;
    }

    @Nonnull
    protected MavenSession getSession() {
        return this.session;
    }
    // </editor-fold>

    // <editor-fold desc="Configuration Getters">
    @Nonnull
    public String getGameVersion() {
        return this.gameVersion;
    }

    @Nonnull
    public String getMappingVersion() {
        return this.mappingVersion;
    }

    @Nonnull
    public String getModule() {
        return this.module;
    }

    @Nonnull
    public File getPatchDirectory() {
        return this.patchDirectory;
    }

    @Nonnull
    public File getSourceDirectory() {
        return this.sourceDirectory;
    }

    @Nonnull
    public File getResourceDirectory() {
        return this.resourceDirectory;
    }
    // </editor-fold>

    /**
     * "Wraps" a temporary file to ensure their deletion after processes involving its lifespan
     * finish execution.
     */
    protected <E extends Exception> void temporary(@Nonnull PathConsumer<E> consumer) throws E, IOException {
        Path tmp = Files.createTempFile("mvn_mc", "tmp");

        try {
            consumer.accept(tmp);
        } finally {
            Files.delete(tmp);
        }
    }

    protected <E extends Exception> void temporary(@Nonnegative int amount, @Nonnull MultiPathConsumer<E> consumer) throws E, IOException {
        Path tmp[] = new Path[amount];

        for (int i = 0; i < tmp.length; ++i) {
            tmp[i] = Files.createTempFile("mvn_mc", "tmp");
        }

        try {
            consumer.accept(tmp);
        } finally {
            IOException exception = null;

            for (Path path : tmp) {
                try {
                    Files.delete(path);
                } catch (IOException ex) {
                    exception = ex;
                }
            }

            if (exception != null) {
                throw new IOException("One or more temporary files could not be deleted: " + exception.getMessage(), exception);
            }
        }
    }

    /**
     * Verifies whether the specified set of configuration properties is within their expected
     * bounds.
     */
    protected void verifyProperties(@Nonnull String... propertyNames) throws MojoExecutionException {
        for (String propertyName : propertyNames) {
            switch (propertyName) {
                case "module":
                    if (!"server".equals(this.getModule()) && !"client".equals(this.getModule())) {
                        throw new MojoExecutionException("Invalid module \"" + this.getModule() + "\" expected server or client");
                    }
                    break;
                case "patchDirectory":
                    this.verifyDirectory(this.patchDirectory.toPath());
                    break;
                case "sourceDirectory":
                    this.verifyDirectory(this.sourceDirectory.toPath());
                    break;
                case "resourceDirectory":
                    this.verifyDirectory(this.resourceDirectory.toPath());
                    break;
            }
        }
    }

    /**
     * Verifies whether a specified directory conforms to the correct bounds and makes sure it
     * exists.
     */
    private void verifyDirectory(@Nonnull Path directory) throws MojoExecutionException {
        if (Files.isRegularFile(directory)) {
            throw new MojoExecutionException("Directory \"" + directory.toAbsolutePath() + "\" is occupied by a file");
        }

        if (Files.notExists(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (IOException ex) {
                throw new MojoExecutionException("Cannot create directory \"" + directory.toAbsolutePath() + "\": " + ex.getMessage(), ex);
            }
        } else {
            if (!Files.isReadable(directory)) {
                throw new MojoExecutionException("Directory \"" + directory.toAbsolutePath() + "\" is not readable");
            }

            if (!Files.isWritable(directory)) {
                throw new MojoExecutionException("Directory \"" + directory.toAbsolutePath() + "\" is not writable");
            }
        }
    }

    /**
     * Provides a simple consumer which is capable of throwing any kind of exception.
     *
     * @param <E> an exception type.
     */
    @FunctionalInterface
    public interface PathConsumer<E extends Exception> {
        void accept(@Nonnull Path p) throws E;
    }

    /**
     * Provides a simple consumer which is capable of throwing any kind of exception and accepting
     * multiple values.
     *
     * @param <E> an exception type.
     */
    @FunctionalInterface
    public interface MultiPathConsumer<E extends Exception> {
        void accept(@Nonnull Path... p) throws E;
    }
}
