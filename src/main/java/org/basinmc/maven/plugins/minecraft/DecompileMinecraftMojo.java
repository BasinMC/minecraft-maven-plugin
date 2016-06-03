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

import com.google.common.io.ByteStreams;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.SpecialSource;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.md_5.specialsource.transformer.MinecraftCodersPack;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * <strong>Decompile Minecraft Mojo</strong>
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "decompile",
        threadSafe = true,
        defaultPhase = LifecyclePhase.GENERATE_SOURCES
)
public class DecompileMinecraftMojo extends AbstractMinecraftMojo {
        public static final String MCP_URL_PATTERN = "http://export.mcpbot.bspk.rs/mcp_%1$s/%3$s-%2$s/mcp_%1$s-%3$s-%2$s.zip";
        public static final String SRG_URL_PATTERN = "https://bitbucket.org/ProfMobius/mcpbot/raw/default/mcp-%s-csrg.zip";

        /**
         * Creates a mapped artifact.
         *
         * @throws MojoFailureException when an error occurs during remapping or acquiring of the necessary mapping files.
         */
        @Nonnull
        private void createMappedArtifact() throws MojoFailureException {
                this.getLog().info("Generating a mapped artifact");

                try {
                        // initialize a set of repository paths and temporary paths
                        Path vanillaArtifact = this.locateArtifact(this.module).map((a) -> a.getFile().toPath()).orElseThrow(() -> new MojoFailureException("Could not locate vanilla artifact"));
                        Path srgArchive = Files.createTempFile("mappings", ".zip");
                        Path mcpArchive = Files.createTempFile("mappings", ".zip");
                        Path mappingsDirectory = Files.createTempDirectory("mappings");
                        Path mappedArtifact = Files.createTempFile("minecraft_mapped", "jar");
                        Path mappedArtifactDescriptor = Files.createTempFile("minecraft_mapped", "pom");

                        // detect the correct MCP version and version type
                        String mcpVersion = this.mcpVersion.substring(this.mcpVersion.indexOf('-') + 1);
                        String mcpReleaseType = this.mcpVersion.substring(0, this.mcpVersion.indexOf('-'));

                        // download and extract all mappings
                        this.downloadArtifact(srgArchive, String.format(SRG_URL_PATTERN, this.gameVersion));
                        this.downloadArtifact(mcpArchive, String.format(MCP_URL_PATTERN, mcpReleaseType, this.gameVersion, mcpVersion));

                        this.extract(srgArchive, mappingsDirectory);
                        this.extract(mcpArchive, mappingsDirectory);

                        // apply SRG and MCP mappings using SpecialSource
                        SpecialSource.kill_lvt = true;
                        SpecialSource.kill_source = true;

                        MinecraftCodersPack codersPack = new MinecraftCodersPack(mappingsDirectory.resolve("fields.csv").toFile(), mappingsDirectory.resolve("methods.csv").toFile(), null);

                        JarMapping mapping = new JarMapping();
                        mapping.loadMappings(new BufferedReader(new FileReader(mappingsDirectory.resolve("joined.csrg").toFile())), null, codersPack, false);

                        JarRemapper remapper = new JarRemapper(mapping);
                        Jar jar = Jar.init(vanillaArtifact.toFile());

                        JointProvider inheritanceProvider = new JointProvider();
                        inheritanceProvider.add(new JarProvider(jar));
                        mapping.setFallbackInheritanceProvider(inheritanceProvider);

                        remapper.remapJar(jar, mappedArtifact.toFile());

                        // store the resulting jar in the local repository
                        this.generateArtifactDescriptor(mappedArtifactDescriptor, this.module + MAPPED_SUFFIX, "-" + this.mcpVersion);
                        this.installArtifact(this.module + MAPPED_SUFFIX, "-" + this.mcpVersion, mappedArtifactDescriptor, mappedArtifact);

                        // clean up all remaining temporary files
                        try {
                                Files.deleteIfExists(mappedArtifact);
                        } catch (IOException ex) {
                                this.getLog().warn("Could not delete temporary file: " + ex.getMessage());
                                this.getLog().warn("You may want to delete " + mappedArtifact.toAbsolutePath().toString() + " manually.");
                        }

                        try {
                                Files.deleteIfExists(mappedArtifactDescriptor);
                        } catch (IOException ex) {
                                this.getLog().warn("Could not delete temporary file: " + ex.getMessage());
                                this.getLog().warn("You may want to delete " + mappedArtifactDescriptor.toAbsolutePath().toString() + " manually.");
                        }

                        try {
                                FileUtils.deleteDirectory(mappingsDirectory.toFile());
                        } catch (IOException ex) {
                                this.getLog().warn("Could not delete temporary file: " + ex.getMessage());
                                this.getLog().warn("You may want to delete " + mappingsDirectory.toAbsolutePath().toString() + " manually.");
                        }
                } catch (ArtifactResolutionException ex) {
                        throw new MojoFailureException("Could not resolve vanilla artifact: " + ex.getMessage(), ex);
                } catch (IOException ex) {
                        throw new MojoFailureException("Could not create temporary mapping files: " + ex.getMessage(), ex);
                }
        }

        /**
         * Creates a source code artifact and stores it in the local maven repository.
         *
         * @throws MojoFailureException when an error occurs while stripping or decompiling the jar.
         */
        @Nonnull
        private void createSourceArtifact() throws MojoFailureException {
                try {
                        // locate a mapped artifact or create a fresh version
                        Optional<Path> mappedArtifactOptional = this.locateArtifact(module + MAPPED_SUFFIX, "-" + this.mcpVersion).map((a) -> a.getFile().toPath());

                        if (!mappedArtifactOptional.isPresent()) {
                                this.getLog().info("No cached version of " + MINECRAFT_GROUP_ID + ":" + module + MAPPED_SUFFIX + ":" + this.gameVersion + "-" + this.mcpVersion + " found in local repository");
                                this.createMappedArtifact();

                                mappedArtifactOptional = this.locateArtifact(module + MAPPED_SUFFIX, "-" + this.mcpVersion).map((a) -> a.getFile().toPath());
                        } else {
                                this.getLog().info("Found cached version of " + MINECRAFT_GROUP_ID + ":" + module + MAPPED_SUFFIX + ":" + this.gameVersion + "-" + this.mcpVersion + " found in local repository");
                        }

                        Path mappedArtifact = mappedArtifactOptional.get();
                        Path outputDirectory = Files.createTempDirectory("minecraft_decompile");

                        Path strippedArtifact = outputDirectory.resolve("minecraft_stripped.jar");
                        Path sourceOutputDirectory = outputDirectory.resolve("sources");
                        Path sourceArtifactDescriptor = Files.createTempFile("minecraft_source", ".pom");
                        Path sourceArtifact = Files.createTempFile("minecraft_source", "jar");

                        this.getLog().info("Stripping dependencies from jar");
                        try (ZipFile file = new ZipFile(mappedArtifact.toFile())) {
                                Enumeration<? extends ZipEntry> enumeration = file.entries();

                                try (FileOutputStream outputStream = new FileOutputStream(strippedArtifact.toFile())) {
                                        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                                                while (enumeration.hasMoreElements()) {
                                                        ZipEntry entry = enumeration.nextElement();

                                                        if (!entry.getName().equalsIgnoreCase("log4j2.xml") && !entry.getName().equalsIgnoreCase("yggdrasil_session_pubkey.der") && !entry.getName().equalsIgnoreCase("pack.png") && !entry.getName().startsWith("net") && !entry.getName().startsWith("assets")) {
                                                                continue;
                                                        }

                                                        if (entry.isDirectory()) {
                                                                zipOutputStream.putNextEntry(entry);
                                                                continue;
                                                        }

                                                        try (InputStream inputStream = file.getInputStream(entry)) {
                                                                zipOutputStream.putNextEntry(entry);
                                                                ByteStreams.copy(inputStream, zipOutputStream);
                                                        }
                                                }
                                        }
                                }
                        } catch (IOException ex) {
                                throw new MojoFailureException("Could not strip artifact: " + ex.getMessage(), ex);
                        }

                        this.getLog().info("Decompiling Minecraft " + this.module + " " + this.gameVersion);
                        Files.createDirectories(sourceOutputDirectory);
                        ConsoleDecompiler.main(new String[]{"-din=1", "-rbr=0", "-rsy=1", "-dgs=1", "-asc=1", "-log=ERROR", strippedArtifact.toAbsolutePath().toString(), sourceOutputDirectory.toAbsolutePath().toString()});

                        this.getLog().info("Formatting source code ...");
                        try (ZipFile file = new ZipFile(sourceOutputDirectory.resolve("minecraft_stripped.jar").toFile())) {
                                try (FileOutputStream outputStream = new FileOutputStream(sourceArtifact.toFile())) {
                                        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                                                Enumeration<? extends ZipEntry> enumeration = file.entries();
                                                Formatter formatter = new Formatter();

                                                while (enumeration.hasMoreElements()) {
                                                        ZipEntry entry = enumeration.nextElement();

                                                        try (InputStream inputStream = file.getInputStream(entry)) {
                                                                // pass on directories as is
                                                                if (entry.isDirectory()) {
                                                                        zipOutputStream.putNextEntry(entry);
                                                                        continue;
                                                                }

                                                                // do not process unknown file types
                                                                if (!entry.getName().endsWith(".java") && !entry.getName().endsWith(".xml")) {
                                                                        zipOutputStream.putNextEntry(entry);
                                                                        ByteStreams.copy(inputStream, zipOutputStream);
                                                                        continue;
                                                                }

                                                                // only convert line feeds for XML files
                                                                if (entry.getName().endsWith(".xml")) {
                                                                        ZipEntry outputEntry = new ZipEntry(entry.getName());
                                                                        zipOutputStream.putNextEntry(outputEntry);

                                                                        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                                                                                try (BufferedReader bufferedReader = new BufferedReader(reader)) {
                                                                                        String line;

                                                                                        while ((line = bufferedReader.readLine()) != null) {
                                                                                                zipOutputStream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                                                                                        }
                                                                                }
                                                                        }

                                                                        continue;
                                                                }

                                                                // apply google style to java files
                                                                ZipEntry outputEntry = new ZipEntry(entry.getName());
                                                                zipOutputStream.putNextEntry(outputEntry);

                                                                try {
                                                                        String output = formatter.formatSource(IOUtil.toString(inputStream));
                                                                        zipOutputStream.write(output.getBytes(StandardCharsets.UTF_8));
                                                                } catch (FormatterException ex) {
                                                                        this.getLog().warn("Could not format file " + entry.getName() + ": " + ex.getMessage(), ex);
                                                                        ByteStreams.copy(inputStream, zipOutputStream);
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }

                        this.generateArtifactDescriptor(sourceArtifactDescriptor, this.module + SOURCE_SUFFIX, "-" + this.mcpVersion);
                        this.installArtifact(this.module + SOURCE_SUFFIX, "-" + this.mcpVersion, sourceArtifactDescriptor, sourceArtifact);

                        try {
                                Files.deleteIfExists(strippedArtifact);
                        } catch (IOException ex) {
                                this.getLog().warn("Could not delete temporary file: " + ex.getMessage());
                                this.getLog().warn("You may want to delete " + strippedArtifact.toAbsolutePath().toString() + " manually.");
                        }

                        try {
                                FileUtils.deleteDirectory(outputDirectory.toFile());
                        } catch (IOException ex) {
                                this.getLog().warn("Could not delete temporary file: " + ex.getMessage());
                                this.getLog().warn("You may want to delete " + outputDirectory.toAbsolutePath().toString() + " manually.");
                        }

                        try {
                                Files.deleteIfExists(sourceArtifactDescriptor);
                        } catch (IOException ex) {
                                this.getLog().warn("Could not delete temporary file: " + ex.getMessage());
                                this.getLog().warn("You may want to delete " + sourceArtifactDescriptor.toAbsolutePath().toString() + " manually.");
                        }
                } catch (ArtifactResolutionException ex) {
                        throw new MojoFailureException("Could not resolve mapped artifact: " + ex.getMessage(), ex);
                } catch (IOException ex) {
                        throw new MojoFailureException("Could not create temporary file: " + ex.getMessage(), ex);
                }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoExecutionException, MojoFailureException {
                super.execute();

                try {
                        Optional<Path> sourceArtifactOptional = this.locateArtifact(module + SOURCE_SUFFIX, "-" + this.mcpVersion).map((a) -> a.getFile().toPath());

                        if (!sourceArtifactOptional.isPresent()) {
                                this.getLog().info("No cached version of " + MINECRAFT_GROUP_ID + ":" + module + SOURCE_SUFFIX + ":" + this.gameVersion + "-" + this.mcpVersion + " found in local repository");
                                this.createSourceArtifact();

                                sourceArtifactOptional = this.locateArtifact(module + SOURCE_SUFFIX, "-" + this.mcpVersion).map((a) -> a.getFile().toPath());
                        } else {
                                this.getLog().info("Found cached version of " + MINECRAFT_GROUP_ID + ":" + module + SOURCE_SUFFIX + ":" + this.gameVersion + "-" + this.mcpVersion + " in local repository");
                        }

                        this.getLog().info("Extracting sources");
                        this.extract(sourceArtifactOptional.get(), this.sourceDirectory.toPath());

                        this.project.addCompileSourceRoot(this.sourceDirectory.toString());
                } catch (ArtifactResolutionException ex) {
                        throw new MojoFailureException("Could not resolve source artifact: " + ex.getMessage(), ex);
                } catch (IOException ex) {
                        throw new MojoFailureException("Could not extract source artifact: " + ex.getMessage(), ex);
                }
        }

        /**
         * Extracts a ZIP archive.
         *
         * @param archivePath     an archive path.
         * @param outputDirectory an output directory.
         * @throws IOException when extraction fails.
         */
        private void extract(@Nonnull Path archivePath, @Nonnull Path outputDirectory) throws IOException {
                try (ZipFile file = new ZipFile(archivePath.toFile())) {
                        Enumeration<? extends ZipEntry> enumeration = file.entries();

                        while (enumeration.hasMoreElements()) {
                                ZipEntry entry = enumeration.nextElement();
                                Path outputPath = outputDirectory.resolve(entry.getName());

                                if (entry.isDirectory()) {
                                        Files.createDirectories(outputPath);
                                        continue;
                                }

                                Files.createDirectories(outputPath.getParent());

                                try (InputStream inputStream = file.getInputStream(entry)) {
                                        try (ReadableByteChannel inputChannel = Channels.newChannel(inputStream)) {
                                                try (FileChannel outputChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                                                        outputChannel.transferFrom(inputChannel, 0, Long.MAX_VALUE);
                                                }
                                        }
                                }
                        }
                }
        }
}
