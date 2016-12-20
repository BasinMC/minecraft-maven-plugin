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
package org.basinmc.maven.plugins.minecraft.source;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.basinmc.maven.plugins.minecraft.AbstractArtifactMojo;
import org.basinmc.maven.plugins.minecraft.launcher.DownloadDescriptor;
import org.basinmc.maven.plugins.minecraft.launcher.VersionIndex;
import org.basinmc.maven.plugins.minecraft.launcher.VersionMetadata;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.NoSuchElementException;

/**
 * Fetches a Minecraft module from the remote servers unless a local version is already present
 * within the local maven repository.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "fetch-module",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.INITIALIZE
)
public class FetchModuleMojo extends AbstractArtifactMojo {

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.verifyProperties("module", "gameVersion");

        this.getLog().info("Fetching Minecraft module (" + this.getModule() + " artifact of version " + this.getGameVersion() + ")");

        try {
            if (!this.findArtifact(this.createArtifactWithClassifier(MINECRAFT_GROUP_ID, this.getModule(), this.getGameVersion(), VANILLA_CLASSIFIER)).isPresent()) {
                this.fetchArtifact();
            } else {
                this.getLog().info("Skipping download of Minecraft module - Located cached artifact");
            }
        } catch (ArtifactResolutionException ex) {
            throw new MojoFailureException("Failed to resolve Minecraft module artifact: " + ex.getMessage(), ex);
        } catch (ArtifactInstallationException ex) {
            throw new MojoFailureException("Failed to install Minecraft module artifact: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to read/write temporary file or access remote server: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new MojoFailureException("Failed to execute task: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetches and installs the Minecraft module artifact.
     */
    private void fetchArtifact() throws Exception {
        VersionIndex index = VersionIndex.fetch();
        VersionMetadata metadata = index.getMetadata(this.getGameVersion()).orElseThrow(() -> new NoSuchElementException("No such game version: " + this.getGameVersion()));

        Artifact artifact = this.createArtifactWithClassifier(MINECRAFT_GROUP_ID, this.getModule(), this.getGameVersion(), VANILLA_CLASSIFIER);
        DownloadDescriptor descriptor = ("server".equals(this.getModule()) ? metadata.getServerDownload() : metadata.getClientDownload());

        this.temporary((a) -> {
            descriptor.fetch(a);

            this.temporary((m) -> {
                this.getLog().info("Storing Minecraft module as artifact " + this.getArtifactCoordinateString(artifact));

                {
                    Model model = new Model();

                    model.setGroupId(MINECRAFT_GROUP_ID);
                    model.setArtifactId(this.getModule());
                    model.setVersion(this.getGameVersion());
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

                    try (FileOutputStream outputStream = new FileOutputStream(m.toFile())) {
                        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
                            (new MavenXpp3Writer()).write(writer, model);
                        }
                    }
                }

                this.installArtifact(artifact, a, m);
            });
        });
    }
}
