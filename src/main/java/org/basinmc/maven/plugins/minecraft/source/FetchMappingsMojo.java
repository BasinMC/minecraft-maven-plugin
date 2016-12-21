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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Fetches and caches the configured MCP mapping artifact within the local repository.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "fetch-mappings",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.INITIALIZE
)
@Immutable
@ThreadSafe
public class FetchMappingsMojo extends AbstractArtifactMojo {
    private static final String MCP_URL = "http://export.mcpbot.bspk.rs/mcp_%1$s/%3$s-%2$s/mcp_%1$s-%3$s-%2$s.zip";
    private static final String MCP_LIVE_URL = "http://export.mcpbot.bspk.rs/%s.csv";
    private static final String SRG_URL = "http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp/%1$s/mcp-%1$s-csrg.zip";

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.verifyProperties("module", "gameVersion", "mappingVersion");

        try {
            {
                Artifact artifact = this.createArtifact(MINECRAFT_GROUP_ID, "mappings-srg", this.getGameVersion(), "zip");
                this.getLog().info("Fetching SRG mappings for Minecraft " + this.getGameVersion());

                if (!this.findArtifact(artifact).isPresent()) {
                    this.populateSrgMappingsArtifact();
                } else {
                    this.getLog().info("Skipping SRG mappings - Cached");
                }
            }

            {
                Artifact artifact = this.createArtifact(MINECRAFT_GROUP_ID, "mappings-mcp", ("live".equals(this.getMappingVersion()) ? MCP_LIVE_VERSION : this.getMappingVersion()), "zip");
                this.getLog().info("Fetching MCP mappings v" + this.getMappingVersion());

                try {
                    if (!this.findArtifact(artifact).filter((p) -> this.isSnapshotArtifactValid(artifact, p)).isPresent()) {
                        this.populateMcpMappingsArtifact();
                    } else {
                        this.getLog().info("Skipping MCP mappings - Cached");
                    }
                } catch (RuntimeException ex) {
                    Throwable inner = ex.getCause();

                    if (inner instanceof IOException) {
                        throw (IOException) inner;
                    }

                    throw ex;
                }
            }
        } catch (ArtifactResolutionException ex) {
            throw new MojoFailureException("Failed to resolve cached artifact: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to verify local repository: " + ex.getMessage(), ex);
        }
    }

    /**
     * Populates the MCP mappings artifact within the local repository.
     */
    private void populateMcpMappingsArtifact() throws MojoFailureException {
        try {
            this.temporary((a) -> {
                Artifact artifact = this.createArtifact(MINECRAFT_GROUP_ID, MCP_ARTIFACT_ID, ("live".equals(this.getMappingVersion()) ? MCP_LIVE_VERSION : this.getMappingVersion()), "zip");

                if ("live".equals(this.getMappingVersion())) {
                    this.getLog().warn("    MCP Live Mappings    ");
                    this.getLog().warn(" ----------------------- ");
                    this.getLog().warn("  This will most likely  ");
                    this.getLog().warn("  break your build       ");
                    this.getLog().warn(" ----------------------- ");
                    this.getLog().warn("  USE AT YOUR OWN RISK   ");

                    try (FileOutputStream outputStream = new FileOutputStream(a.toFile())) {
                        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
                            {
                                ZipEntry entry = new ZipEntry("fields.csv");
                                zip.putNextEntry(entry);
                                {
                                    this.fetch(new URL(String.format(MCP_LIVE_URL, "fields")).toURI(), zip);
                                }
                            }
                            {
                                ZipEntry entry = new ZipEntry("methods.csv");
                                zip.putNextEntry(entry);
                                {
                                    this.fetch(new URL(String.format(MCP_LIVE_URL, "methods")).toURI(), zip);
                                }
                            }
                            {
                                ZipEntry entry = new ZipEntry("params.csv");
                                zip.putNextEntry(entry);
                                {
                                    this.fetch(new URL(String.format(MCP_LIVE_URL, "params")).toURI(), zip);
                                }
                            }

                            zip.closeEntry();
                        }
                    }
                } else {
                    String[] elements = this.getMappingVersion().split("-");
                    this.fetch(String.format(MCP_URL, elements[0], this.getGameVersion(), elements[1]), a);
                }

                this.temporary((m) -> {
                    this.getLog().info("Storing mappings as artifact " + this.getArtifactCoordinateString(artifact));

                    {
                        Model model = new Model();

                        model.setGroupId(MINECRAFT_GROUP_ID);
                        model.setArtifactId(MCP_ARTIFACT_ID);
                        model.setVersion(artifact.getVersion());
                        model.setPackaging("zip");

                        Organization organization = new Organization();
                        organization.setName("MCP Team");
                        organization.setUrl("http://www.modcoderpack.com/website/");
                        model.setOrganization(organization);

                        License license = new License();
                        license.setName("MCP License");
                        license.setUrl("http://www.modcoderpack.com/website/releases");
                        license.setDistribution("manual");
                        model.addLicense(license);

                        try (FileOutputStream outputStream = new FileOutputStream(m.toFile())) {
                            try (OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
                                (new MavenXpp3Writer()).write(writer, model);
                            }
                        }
                    }

                    this.installArtifact(artifact, m, a);
                });
            });
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to write or retrieve one or more artifacts: " + ex.getMessage(), ex);
        } catch (ArtifactInstallationException ex) {
            throw new MojoFailureException("Failed to install artifact: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new MojoFailureException("Caught unexpected exception: " + ex.getMessage(), ex);
        }
    }

    /**
     * Populates the correct SRG mappings version within the local repository.
     */
    private void populateSrgMappingsArtifact() throws MojoFailureException {
        try {
            this.temporary((a) -> {
                Artifact artifact = this.createArtifact(MINECRAFT_GROUP_ID, SRG_ARTIFACT_ID, this.getGameVersion(), "zip");

                this.getLog().info("Storing SRG mappings as artifact " + this.getArtifactCoordinateString(artifact));
                this.fetch(String.format(SRG_URL, this.getGameVersion()), a);

                this.temporary((m) -> {
                    Model model = new Model();

                    model.setGroupId(MINECRAFT_GROUP_ID);
                    model.setArtifactId(this.getModule());
                    model.setVersion(this.getGameVersion());
                    model.setPackaging("jar");

                    Organization organization = new Organization();
                    organization.setName("MCP Team");
                    organization.setUrl("http://www.modcoderpack.com/website/");
                    model.setOrganization(organization);

                    License license = new License();
                    license.setName("MCP License");
                    license.setUrl("http://www.modcoderpack.com/website/releases");
                    license.setDistribution("manual");
                    model.addLicense(license);

                    try (FileOutputStream outputStream = new FileOutputStream(m.toFile())) {
                        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
                            (new MavenXpp3Writer()).write(writer, model);
                        }
                    }

                    this.installArtifact(artifact, m, a);
                });
            });
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to write artifact or decode server response: " + ex.getMessage(), ex);
        } catch (ArtifactInstallationException ex) {
            throw new MojoFailureException("Failed to install artifact: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new MojoFailureException("Unknown Error: " + ex.getMessage(), ex);
        }
    }

    /**
     * Provides a consumer for accessing three temporary files at once.
     */
    @FunctionalInterface
    private interface PathConsumer<E extends Exception> {
        void accept(@Nonnull Path a, @Nonnull Path b, @Nonnull Path c) throws E;
    }
}
