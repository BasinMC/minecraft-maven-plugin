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
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    private String patchDirectory;
    @Parameter(defaultValue = "${project.basedir}/src/minecraft/java", required = true)
    private String sourceDirectory;
    @Parameter(defaultValue = "${project.basedir}/src/minecraft/resource", required = true)
    private String resourceDirectory;
    // </editor-fold>

    // <editor-fold desc="Component Getters">
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
    public String getPatchDirectory() {
        return this.patchDirectory;
    }

    @Nonnull
    public String getSourceDirectory() {
        return this.sourceDirectory;
    }

    @Nonnull
    public String getResourceDirectory() {
        return this.resourceDirectory;
    }
    // </editor-fold>

    /**
     * "Wraps" a temporary file to ensure their deletion after processes involving its lifespan
     * finish execution.
     */
    protected <E extends Exception> void temporary(@Nonnull PathConsumer<E> consumer) throws E, IOException {
        Path tmp = Files.createTempFile("mvn_mc", "mc");

        try {
            consumer.accept(tmp);
        } finally {
            Files.delete(tmp);
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
}
