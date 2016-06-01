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

import edu.emory.mathcs.backport.java.util.Collections;
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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;

import javax.annotation.Nonnull;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * <strong>Download Minecraft Mojo</strong>
 *
 * Downloads a Minecraft executable from the official servers.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "download",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.INITIALIZE
)
public class DownloadMinecraftMojo extends AbstractMinecraftMojo {
        public static final String CLIENT_JAR_LOCATION_TEMPLATE = "https://s3.amazonaws.com/Minecraft.Download/versions/%1$s/%1$s.jar";
        public static final String SERVER_JAR_LOCATION_TEMPLATE = "https://s3.amazonaws.com/Minecraft.Download/versions/%1$s/minecraft_server.%1$s.jar";

        @Component
        private ArtifactFactory artifactFactory;
        @Component
        private ArtifactInstaller artifactInstaller;
        @Component
        private ArtifactResolver artifactResolver;
        @Component
        private MavenSession session;

        /**
         * Downloads the artifact in case no local cached version could be found.
         *
         * @param artifactPath a temporary artifact path.
         * @throws MojoFailureException when an error occurs while attempting to fetch an artifact.
         */
        private void downloadArtifact(@Nonnull Path artifactPath) throws MojoFailureException {
                this.getLog().info("Downloading module from Mojang (this may take a long time)");

                try {
                        final String url;

                        if (this.module.equalsIgnoreCase("client")) {
                                url = String.format(CLIENT_JAR_LOCATION_TEMPLATE, this.gameVersion);
                        } else {
                                url = String.format(SERVER_JAR_LOCATION_TEMPLATE, this.gameVersion);
                        }

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
                super.execute();

                if (!this.locateArtifact()) {
                        this.getLog().info("No cached version of net:minecraft:" + this.module + ":" + this.gameVersion + " found in local repository");

                        try {
                                Path artifactPath = Files.createTempFile("minecraft_" + this.module, ".jar");
                                Path pomPath = Files.createTempFile("minecraft_" + this.module, ".xml");

                                this.downloadArtifact(artifactPath);
                                this.generateArtifactDescriptor(pomPath);
                                this.installArtifact(pomPath, artifactPath);
                        } catch (IOException ex) {
                                throw new MojoFailureException("Could not create a temporary file: " + ex.getMessage(), ex);
                        }
                } else {
                        this.getLog().info("Found cached version of net.minecraft:" + this.module + ":" + this.gameVersion);
                }
        }

        /**
         * Generates an artifact descriptor.
         *
         * @param pomPath a temporary descriptor path.
         * @throws MojoFailureException when an error occurs while attempting to generate the descriptor.
         */
        private void generateArtifactDescriptor(@Nonnull Path pomPath) throws MojoFailureException {
                this.getLog().info("Generating maven artifact descriptor for net.minecraft:" + this.module + ":" + this.gameVersion);
                Model model = new Model();

                model.setGroupId(MINECRAFT_GROUP_ID);
                model.setArtifactId(this.module);
                model.setVersion(this.gameVersion);
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
         * Installs an artifact to the local repository.
         *
         * @param pomPath      a temporary descriptor path.
         * @param artifactPath a temporary artifact path.
         * @throws MojoFailureException when an error occurs while attempting to install the artifact.
         */
        private void installArtifact(@Nonnull Path pomPath, @Nonnull Path artifactPath) throws MojoFailureException {
                this.getLog().info("Installing net.minecraft:" + this.module + ":" + this.gameVersion + " to local repository");
                Artifact artifact = this.artifactFactory.createArtifact(MINECRAFT_GROUP_ID, this.module, this.gameVersion, "compile", "jar");

                ArtifactMetadata metadata = new ProjectArtifactMetadata(artifact, pomPath.toFile());
                artifact.addMetadata(metadata);

                try {
                        this.artifactInstaller.install(artifactPath.toFile(), artifact, this.session.getLocalRepository());
                } catch (ArtifactInstallationException ex) {
                        throw new MojoFailureException("Could not install artifact: " + ex.getMessage(), ex);
                }
        }

        /**
         * Locates an artifact within the local maven repository.
         *
         * @return true if located, false otherwise.
         *
         * @throws MojoFailureException when an error occurs while attempting to resolve the module within the local repository.
         */
        private boolean locateArtifact() throws MojoFailureException {
                Artifact artifact = this.artifactFactory.createBuildArtifact(MINECRAFT_GROUP_ID, this.module, this.gameVersion, "jar");

                try {
                        this.artifactResolver.resolve(artifact, Collections.emptyList(), this.session.getLocalRepository());
                        return true;
                } catch (ArtifactResolutionException e) {
                        throw new MojoFailureException("Could not resolve artifact: " + e.getMessage());
                } catch (ArtifactNotFoundException e) {
                        return false;
                }
        }
}
