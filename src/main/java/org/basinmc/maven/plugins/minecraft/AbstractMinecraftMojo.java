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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;

import javax.annotation.Nonnull;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Optional;

/**
 * <strong>Abstract Mojo</strong>
 *
 * Provides a base mojo implementation which provides access to certain configuration resources.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public abstract class AbstractMinecraftMojo extends AbstractMojo {

        /**
         * <strong>Mapped Suffix</strong>
         *
         * Specifies an artifact suffix to append to all mapped artifacts.
         */
        public static final String MAPPED_SUFFIX = "-mapped";
        /**
         * <strong>Minecraft Group ID</strong>
         *
         * Specifies the group identifier cached artifacts will be installed to.
         */
        public static final String MINECRAFT_GROUP_ID = "org.basinmc.minecraft";
        /**
         * <strong>Source Suffix</strong>
         *
         * Specifies an artifact suffix to append to all source artifacts.
         */
        public static final String SOURCE_SUFFIX = "-source";
        @Component
        private ArtifactFactory artifactFactory;
        @Component
        private ArtifactInstaller artifactInstaller;
        @Component
        private ArtifactResolver artifactResolver;
        /**
         * Specifies the game version to download and map.
         */
        @Parameter(defaultValue = "1.9.4")
        protected String gameVersion;
        /**
         * Specifies the MCP mappings version to apply.
         */
        @Parameter(defaultValue = "snapshot-20160601")
        protected String mcpVersion;
        /**
         * Specifies a list of modules to download decompile and remap.
         * Valid values are: client, server
         */
        @Parameter(defaultValue = "server")
        protected String module;
        /**
         * Stores a reference to the project's configuration.
         */
        @Parameter(property = "project", required = true, readonly = true)
        protected MavenProject project;
        @Component
        private MavenSession session;

        /**
         * Specifies the source output directory.
         */
        @Parameter(defaultValue = "${project.build.directory}/generated-sources/minecraft")
        protected File sourceDirectory;

        /**
         * Downloads the artifact in case no local cached version could be found.
         *
         * @param artifactPath a temporary artifact path.
         * @param url          a url to download from.
         * @throws MojoFailureException when an error occurs while attempting to fetch an artifact.
         */
        protected void downloadArtifact(@Nonnull Path artifactPath, @Nonnull String url) throws MojoFailureException {
                this.getLog().info("Retrieving " + url);

                try {
                        HttpURLConnection connection = (HttpURLConnection) (new URL(url)).openConnection();

                        if (connection.getResponseCode() != 200) {
                                throw new IllegalStateException("Expected status code 200 but got " + connection.getResponseCode());
                        }

                        try (InputStream inputStream = connection.getInputStream()) {
                                try (ReadableByteChannel inputChannel = Channels.newChannel(inputStream)) {
                                        try (FileChannel outputChannel = FileChannel.open(artifactPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                                                outputChannel.transferFrom(inputChannel, 0, Long.MAX_VALUE);
                                        }
                                }
                        }
                } catch (IllegalStateException | IOException ex) {
                        throw new MojoFailureException("Could not read/write artifact: " + ex.getMessage(), ex);
                }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoExecutionException, MojoFailureException {
                if (!this.module.equalsIgnoreCase("client") && !this.module.equalsIgnoreCase("server")) {
                        throw new MojoFailureException("Invalid module name \"" + module + "\"");
                }
        }

        /**
         * Generates an artifact descriptor.
         *
         * @param pomPath       a temporary descriptor path.
         * @param artifactId    an artifact identifier.
         * @param versionSuffix a version suffix.
         * @throws MojoFailureException when an error occurs while attempting to generate the descriptor.
         */
        protected void generateArtifactDescriptor(@Nonnull Path pomPath, @Nonnull String artifactId, @Nonnull String versionSuffix) throws MojoFailureException {
                this.getLog().info("Generating maven artifact descriptor for net.minecraft:" + artifactId + ":" + this.gameVersion + versionSuffix);
                Model model = new Model();

                model.setGroupId(MINECRAFT_GROUP_ID);
                model.setArtifactId(artifactId);
                model.setVersion(this.gameVersion + versionSuffix);
                model.setPackaging("jar");

                Organization organization = new Organization();
                organization.setName("Mojang");
                organization.setUrl("http://mojang.com");
                model.setOrganization(organization);

                License license = new License();
                license.setName("Mojang EULA");
                license.setUrl("https://account.mojang.com/terms");
                license.setDistribution("manual");
                model.addLicense(license);

                try (FileOutputStream outputStream = new FileOutputStream(pomPath.toFile())) {
                        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
                                (new MavenXpp3Writer()).write(writer, model);
                        }
                } catch (IOException ex) {
                        throw new MojoFailureException("Could not write model: " + ex.getMessage(), ex);
                }
        }

        /**
         * Generates an artifact descriptor.
         *
         * @param pomPath    a temporary descriptor path.
         * @param artifactId an artifact identifier.
         * @throws MojoFailureException when an error occurs while attempting to generate the descriptor.
         */
        protected void generateArtifactDescriptor(@Nonnull Path pomPath, @Nonnull String artifactId) throws MojoFailureException {
                this.generateArtifactDescriptor(pomPath, artifactId, "");
        }

        /**
         * Installs an artifact to the local repository.
         *
         * @param artifactId   an artifact identifier.
         * @param pomPath      a temporary descriptor path.
         * @param artifactPath a temporary artifact path.
         * @return the generated artifact.
         *
         * @throws MojoFailureException when an error occurs while attempting to install the artifact.
         */
        protected Artifact installArtifact(@Nonnull String artifactId, @Nonnull Path pomPath, @Nonnull Path artifactPath) throws MojoFailureException {
                return this.installArtifact(artifactId, "", pomPath, artifactPath);
        }

        /**
         * Instals an artifact to the local repository.
         *
         * @param artifactId    an artifact identifier.
         * @param versionSuffix a version suffix.
         * @param pomPath       a temporary descriptor path.
         * @param artifactPath  a temporary artifact path.
         * @return the generated artifact.
         *
         * @throws MojoFailureException when an error occurs while attempting to install the artifact.
         */
        protected Artifact installArtifact(@Nonnull String artifactId, @Nonnull String versionSuffix, @Nonnull Path pomPath, @Nonnull Path artifactPath) throws MojoFailureException {
                this.getLog().info("Installing net.minecraft:" + artifactId + ":" + this.gameVersion + versionSuffix + " to local repository");
                Artifact artifact = this.artifactFactory.createArtifact(MINECRAFT_GROUP_ID, artifactId, this.gameVersion + versionSuffix, "compile", "jar");

                ArtifactMetadata metadata = new ProjectArtifactMetadata(artifact, pomPath.toFile());
                artifact.addMetadata(metadata);

                try {
                        this.artifactInstaller.install(artifactPath.toFile(), artifact, this.session.getLocalRepository());

                        try {
                                Files.deleteIfExists(pomPath);
                        } catch (IOException ex) {
                                this.getLog().warn("Could not delete generated module descriptor: " + ex.getMessage(), ex);
                                this.getLog().warn("You may want to delete " + pomPath.toAbsolutePath().toString() + " manually.");
                        }

                        try {
                                Files.deleteIfExists(artifactPath);
                        } catch (IOException ex) {
                                this.getLog().warn("Could not delete module artifact: " + ex.getMessage(), ex);
                                this.getLog().warn("You may want to delete " + pomPath.toAbsolutePath().toString() + " manually");
                        }

                        return artifact;
                } catch (ArtifactInstallationException ex) {
                        throw new MojoFailureException("Could not install artifact: " + ex.getMessage(), ex);
                }
        }

        /**
         * Locates an artifact in the local repository.
         *
         * @param artifactId an artifact identifier.
         * @return an artifact or, if no local version was found, an empty optional.
         *
         * @throws ArtifactResolutionException when the artifact resolution fails.
         */
        @Nonnull
        protected Optional<Artifact> locateArtifact(@Nonnull String artifactId) throws ArtifactResolutionException {
                return this.locateArtifact(artifactId, "");
        }

        /**
         * Locates an artifact in the local repository.
         *
         * @param artifactId    an artifact identifier.
         * @param versionSuffix a version suffix.
         * @return an artifact or, if no local version was found, an empty optional.
         *
         * @throws ArtifactResolutionException when the artifact resolution fails.
         */
        protected Optional<Artifact> locateArtifact(@Nonnull String artifactId, @Nonnull String versionSuffix) throws ArtifactResolutionException {
                try {
                        Artifact artifact = this.artifactFactory.createBuildArtifact(MINECRAFT_GROUP_ID, artifactId, this.gameVersion + versionSuffix, "jar");
                        this.artifactResolver.resolve(artifact, Collections.emptyList(), this.session.getLocalRepository());
                        return Optional.of(artifact);
                } catch (ArtifactNotFoundException ex) {
                        return Optional.empty();
                }
        }
}
