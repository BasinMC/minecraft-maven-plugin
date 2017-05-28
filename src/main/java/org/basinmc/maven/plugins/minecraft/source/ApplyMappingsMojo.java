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

import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.SpecialSource;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.md_5.specialsource.transformer.MinecraftCodersPack;

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
import org.basinmc.maven.plugins.minecraft.AbstractMappingMojo;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nonnull;

/**
 * Provides a Mojo capable of remapping vanilla jars into a semi-readable form.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "apply-mappings",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.INITIALIZE
)
public class ApplyMappingsMojo extends AbstractMappingMojo {

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.verifyProperties("module", "gameVersion", "mappingVersion");

        Artifact artifact = this.getMappedArtifact();
        this.getLog().info("Mapping module " + this.getModule() + " of version " + this.getGameVersion() + " against SRG " + this.getSrgVersion() + " and MCP " + ("live".equals(this.getMappingVersion()) ? "live mappings" : "version " + this.getMappingVersion()));

        try {
            if (!this.findArtifact(artifact).map((p) -> this.isSnapshotArtifactValid(artifact, p)).isPresent()) {
                this.populateMappedArtifact();
            } else {
                this.getLog().info("Skipping module mapping - Cached");
            }
        } catch (ArtifactResolutionException ex) {
            throw new MojoFailureException("Cannot resolve artifact: " + ex.getMessage(), ex);
        }
    }

    /**
     * Extracts an entry from a ZIP archive to a specified location.
     */
    private void extractEntry(@Nonnull ZipFile file, @Nonnull ZipEntry entry, @Nonnull Path output) throws IOException {
        try (InputStream inputStream = file.getInputStream(entry)) {
            try (ReadableByteChannel inputChannel = Channels.newChannel(inputStream)) {
                try (FileChannel fileChannel = FileChannel.open(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    fileChannel.transferFrom(inputChannel, 0, Long.MAX_VALUE);
                }
            }
        }
    }

    /**
     * Populates the mapped artifact within the local maven repository.
     */
    private void populateMappedArtifact() throws ArtifactResolutionException, MojoFailureException {
        Artifact artifact = this.getMappedArtifact();
        final Path minecraftArtifact;
        final Path srgMappingsArtifact;
        final Path mcpMappingsArtifact;

        {
            Artifact a = this.createArtifactWithClassifier(MINECRAFT_GROUP_ID, this.getModule(), this.getGameVersion(), VANILLA_CLASSIFIER);
            minecraftArtifact = this.findArtifact(a).orElseThrow(() -> new MojoFailureException("Could not locate artifact " + this.getArtifactCoordinateString(a)));
        }

        {
            Artifact a = this.createArtifact(MINECRAFT_GROUP_ID, SRG_ARTIFACT_ID, this.getSrgVersion(), "zip");
            srgMappingsArtifact = this.findArtifact(a).orElseThrow(() -> new MojoFailureException("Could not locate artifact " + this.getArtifactCoordinateString(a)));
        }

        {
            Artifact a = this.createArtifact(MINECRAFT_GROUP_ID, MCP_ARTIFACT_ID, ("live".equals(this.getMappingVersion()) ? MCP_LIVE_VERSION : this.getMappingVersion()), "zip");
            mcpMappingsArtifact = this.findArtifact(a).orElseThrow(() -> new MojoFailureException("Could not locate artifact " + this.getArtifactCoordinateString(a)));
        }

        try {
            this.temporary((a) -> {
                this.temporary(4, (m) -> {
                    final Path srgPath = m[0];
                    final Path mcpFieldsPath = m[1];
                    final Path mcpMethodsPath = m[2];

                    // TODO: Add support for MCP parameter mappings

                    try (ZipFile file = new ZipFile(srgMappingsArtifact.toFile())) {
                        ZipEntry entry = file.getEntry("joined.csrg");

                        this.extractEntry(file, entry, srgPath);
                    }

                    try (ZipFile file = new ZipFile(mcpMappingsArtifact.toFile())){
                        {
                            ZipEntry entry = file.getEntry("fields.csv");
                            this.extractEntry(file, entry, mcpFieldsPath);
                        }

                        {
                            ZipEntry entry = file.getEntry("methods.csv");
                            this.extractEntry(file, entry, mcpMethodsPath);
                        }
                    }

                    // configure SpecialSource to remove bogus debug information
                    SpecialSource.kill_lvt = true;
                    SpecialSource.kill_source = true;

                    MinecraftCodersPack codersPack = new MinecraftCodersPack(mcpFieldsPath.toFile(), mcpMethodsPath.toFile(), null);

                    try (BufferedReader srgReader = new BufferedReader(new FileReader(srgPath.toFile()))) {
                        JarMapping mapping = new JarMapping();
                        mapping.loadMappings(srgReader, null, codersPack, false);

                        JarRemapper remapper = new JarRemapper(mapping);
                        Jar jar = Jar.init(minecraftArtifact.toFile());

                        JointProvider inheritanceProvider = new JointProvider();
                        inheritanceProvider.add(new JarProvider(jar));
                        mapping.setFallbackInheritanceProvider(inheritanceProvider);

                        remapper.remapJar(jar, a.toFile());
                    }
                });

                this.temporary((m) -> {
                    this.getLog().info("Storing mapped module as artifact " + this.getArtifactCoordinateString(artifact));

                    {
                        Model model = new Model();

                        model.setGroupId(MINECRAFT_GROUP_ID);
                        model.setArtifactId(this.getModule());
                        model.setVersion(this.getMappedArtifactVersion());
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

                    this.installArtifact(artifact, m, a);
                });
            });
        } catch (ArtifactInstallationException ex) {
            throw new MojoFailureException("Could not install output artifact: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Cannot read/write source or target artifact(s): " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new MojoFailureException("Caught unexpected exception: " + ex.getMessage(), ex);
        }
    }
}
