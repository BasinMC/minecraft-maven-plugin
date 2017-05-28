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

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.googlejavaformat.java.Formatter;

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
import org.codehaus.plexus.util.IOUtil;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Provides a Mojo which is capable of directly decompiling and caching Minecraft modules in a
 * consistent format.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "decompile-module",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.INITIALIZE
)
public class DecompileModuleMojo extends AbstractMappingMojo {
    private static final List<String> includedRegularFiles = new ImmutableList.Builder<String>()
            .add("log4j2.xml")
            .add("pack.png")
            .add("yggdrasil_session_pubkey.der")
            .build();

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.verifyProperties("module", "gameVersion", "mappingVersion");

        Artifact artifact = this.createArtifactWithClassifier(MINECRAFT_GROUP_ID, this.getModule(), this.getMappingArtifactVersion(), "source");
        this.getLog().info("Decompiling module " + this.getModule() + " with version " + this.getGameVersion() + " using MCP " + ("live".equals(this.getMappingVersion()) ? "live mappings" : "mapping version " + this.getMappingVersion()));

        try {
            if (!this.findArtifact(artifact).map((p) -> this.isSnapshotArtifactValid(artifact, p)).isPresent()) {
                this.populateSourceArtifact();
            } else {
                this.getLog().info("Skipping decompilation - Cached");
            }
        } catch (ArtifactResolutionException ex) {
            throw new MojoFailureException("Failed to resolve artifact: " + ex.getMessage(), ex);
        }
    }

    /**
     * Populates the source artifact within the local repository.
     */
    private void populateSourceArtifact() throws MojoFailureException {
        Artifact artifact = this.createArtifactWithClassifier(MINECRAFT_GROUP_ID, this.getModule(), this.getMappedArtifactVersion(), "source");

        try {
            this.temporary((artifactPath) -> {
                final Path mappedPath;

                {
                    Artifact a = this.getMappedArtifact();
                    mappedPath = this.findArtifact(a).orElseThrow(() -> new MojoFailureException("Could not locate artifact " + this.getArtifactCoordinateString(a)));
                }

                this.temporaryDirectory((tmp) -> {
                    final Path strippedPath = tmp.resolve("stripped.jar");
                    final Path ffWorkingDirectory = tmp.resolve("ff");

                    this.getLog().info("Stripping dependencies from module");
                    Files.createDirectory(ffWorkingDirectory);

                    try (ZipFile inputFile = new ZipFile(mappedPath.toFile())) {
                        try (OutputStream outputStream = new FileOutputStream(strippedPath.toFile())) {
                            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                                Enumeration<? extends ZipEntry> entries = inputFile.entries();

                                while (entries.hasMoreElements()) {
                                    ZipEntry entry = entries.nextElement();
                                    String name = entry.getName();

                                    if (!name.startsWith("assets") && !name.startsWith("net") && !includedRegularFiles.contains(name)) {
                                        continue;
                                    }

                                    zipOutputStream.putNextEntry(new ZipEntry(name));

                                    if (!entry.isDirectory()) {
                                        try (InputStream inputStream = inputFile.getInputStream(entry)) {
                                            ByteStreams.copy(inputStream, zipOutputStream);
                                        }
                                    }
                                }

                                zipOutputStream.closeEntry();
                            }
                        }
                    }

                    this.getLog().info("Decompiling module");

                    Map<String, Integer> ffFlags = new HashMap<>();
                    ffFlags.put(IFernflowerPreferences.DECOMPILE_INNER, 1);
                    ffFlags.put(IFernflowerPreferences.REMOVE_BRIDGE, 0);
                    ffFlags.put(IFernflowerPreferences.REMOVE_SYNTHETIC, 1);
                    ffFlags.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, 1);
                    ffFlags.put(IFernflowerPreferences.ASCII_STRING_CHARACTERS, 1);
                    List<String> args = new ArrayList<>();

                    ffFlags.forEach((pref, value) -> args.add("-" + pref + "=" + String.valueOf(value)));
                    args.add("-log=ERROR");
                    args.add(strippedPath.toAbsolutePath().toString());
                    args.add(ffWorkingDirectory.toAbsolutePath().toString());

                    ConsoleDecompiler.main(args.toArray(new String[0]));


                    tmp = ffWorkingDirectory.resolve(strippedPath.getFileName());

                    if (Files.notExists(tmp)) {
                        throw new MojoFailureException("Failed to decompile module: Unknown fernflower error");
                    }

                    this.getLog().info("Reformatting code");
                    Formatter formatter = new Formatter();

                    try (ZipFile file = new ZipFile(tmp.toFile())) {
                        try (FileOutputStream outputStream = new FileOutputStream(artifactPath.toFile())) {
                            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                                Enumeration<? extends ZipEntry> enumeration = file.entries();

                                while (enumeration.hasMoreElements()) {
                                    ZipEntry entry = enumeration.nextElement();
                                    String name = entry.getName();

                                    try (InputStream inputStream = file.getInputStream(entry)) {
                                        zipOutputStream.putNextEntry(new ZipEntry(name));

                                        if (name.endsWith(".java")) {
                                            zipOutputStream.write(formatter.formatSource(IOUtil.toString(inputStream)).getBytes(StandardCharsets.UTF_8));
                                        } else if (name.endsWith(".xml")) {
                                            zipOutputStream.write(IOUtil.toString(inputStream).replaceAll("\r", "").getBytes(StandardCharsets.UTF_8));
                                        } else {
                                            ByteStreams.copy(inputStream, zipOutputStream);
                                        }
                                    }
                                }

                                zipOutputStream.closeEntry();
                            }
                        }
                    }

                    this.temporary((modelPath) -> {
                        this.getLog().info("Storing module sources as artifact " + this.getArtifactCoordinateString(artifact));

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

                            try (FileOutputStream outputStream = new FileOutputStream(modelPath.toFile())) {
                                try (OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
                                    (new MavenXpp3Writer()).write(writer, model);
                                }
                            }
                        }

                        this.installArtifact(artifact, modelPath, artifactPath);
                    });
                });
            });
        } catch (ArtifactInstallationException ex) {
            throw new MojoFailureException("Failed to install artifact: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Cannot read/write source or target artifact(s): " + ex.getMessage(), ex);
        } catch (MojoFailureException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MojoFailureException("Caught unexpected exception: " + ex.getMessage(), ex);
        }
    }
}
