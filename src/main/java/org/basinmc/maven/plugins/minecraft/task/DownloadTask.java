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
package org.basinmc.maven.plugins.minecraft.task;

import org.apache.maven.plugin.MojoFailureException;
import org.basinmc.maven.plugins.minecraft.MinecraftMojo;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * <strong>Download Task</strong>
 *
 * Downloads pre-built Minecraft jars from the official servers.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public class DownloadTask extends AbstractTask {
        public static final String CLIENT_JAR_LOCATION_TEMPLATE = "https://s3.amazonaws.com/Minecraft.Download/versions/%1$s/%1$s.jar";
        public static final String SEARGE_MAPPINGS_LOCATION_TEMPLATE = "https://bitbucket.org/ProfMobius/mcpbot/raw/default/mcp-%s-csrg.zip";
        public static final String SERVER_JAR_LOCATION_TEMPLATE = "https://s3.amazonaws.com/Minecraft.Download/versions/%1$s/minecraft_server.%1$s.jar";
        public static final String SNAPSHOT_MAPPINGS_LOCATION_TEMPLATE = "http://export.mcpbot.bspk.rs/mcp_snapshot/%2$s-%1$s/mcp_snapshot-%2$s-%1$s.zip";
        public static final String STABLE_MAPPINGS_LOCATION_TEMPLATE = "http://export.mcpbot.bspk.rs/mcp_stable/%2$s-%1$s/mcp_stable-%2$s-%1$s.zip";

        public DownloadTask(@Nonnull MinecraftMojo mojo) {
                super(mojo);
        }

        /**
         * Attempts to download a single artifact from the official servers.
         *
         * @param module          a module name to store it as.
         * @param template        a url template.
         * @param version         a game version.
         * @param outputDirectory an output directory.
         * @throws IOException when the download fails.
         */
        private void downloadArtifact(@Nonnull String module, @Nonnull String template, @Nonnull String version, @Nonnull Path outputDirectory) throws IOException {
                URL artifactUrl = new URL(String.format(template, version));
                Path outputPath = outputDirectory.resolve(module + ".jar");

                Files.createDirectories(outputDirectory);

                try (InputStream inputStream = artifactUrl.openStream()) {
                        try (ReadableByteChannel inputChannel = Channels.newChannel(inputStream)) {
                                try (FileChannel outputChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                                        outputChannel.transferFrom(inputChannel, 0, Long.MAX_VALUE);
                                }
                        }
                }
        }

        /**
         * Attempts to download a single mapping package from the MCP Bot servers.
         *
         * @param revision        a mappings revision.
         * @param gameVersion     a game version.
         * @param outputDirectory an output directory.
         * @throws IOException when the download fails.
         */
        private void downloadMappings(@Nonnull String revision, @Nonnull String gameVersion, @Nonnull Path outputDirectory) throws IOException {
                final URL packageUrl;

                {
                        String template = (revision.contains("snapshot") ? SNAPSHOT_MAPPINGS_LOCATION_TEMPLATE : STABLE_MAPPINGS_LOCATION_TEMPLATE);

                        if (revision.contains("snapshot")) {
                                revision = revision.substring(9);
                        } else if (revision.contains("stable")) {
                                revision = revision.substring(7);
                        }

                        packageUrl = new URL(String.format(template, gameVersion, revision));
                }

                final Path outputPath = outputDirectory.resolve(gameVersion + "-" + revision + ".zip");
                Files.createDirectories(outputDirectory);

                try (InputStream inputStream = packageUrl.openStream()) {
                        try (ReadableByteChannel inputChannel = Channels.newChannel(inputStream)) {
                                try (FileChannel outputChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                                        outputChannel.transferFrom(inputChannel, 0, Long.MAX_VALUE);
                                }
                        }
                }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoFailureException {
                try {
                        if (this.getMojo().getModule().equalsIgnoreCase("client")) {
                                this.getLog().info("Downloading client (this may take a long time)");
                                this.downloadArtifact("client", CLIENT_JAR_LOCATION_TEMPLATE, this.getMojo().getGameVersion(), this.getMojo().getJarOutputDirectory().toPath());
                        } else if (this.getMojo().getModule().equalsIgnoreCase("server")) {
                                this.getLog().info("Downloading server (this may take a long time)");
                                this.downloadArtifact("server", SERVER_JAR_LOCATION_TEMPLATE, this.getMojo().getGameVersion(), this.getMojo().getJarOutputDirectory().toPath());
                        }

                        this.getLog().info("Downloading SRG Mappings.");
                        this.downloadArtifact("srg", SEARGE_MAPPINGS_LOCATION_TEMPLATE, this.getMojo().getGameVersion(), this.getMojo().getMappingOutputDirectory().toPath());

                        this.getLog().info("Downloading mappings.");
                        this.downloadMappings(this.getMojo().getMappingVersion(), this.getMojo().getGameVersion(), this.getMojo().getMappingOutputDirectory().toPath());

                        this.getLog().info("Extracting SRG mappings.");
                        this.extractZip(this.getMojo().getMappingOutputDirectory().toPath().resolve("srg.jar"), this.getMojo().getMappingOutputDirectory().toPath().resolve("srg"));

                        this.getLog().info("Extracting mappings.");
                        this.extractMappings(this.getMojo().getMappingVersion(), this.getMojo().getGameVersion(), this.getMojo().getMappingOutputDirectory().toPath());
                } catch (IOException ex) {
                        throw new MojoFailureException("Could not download one or more artifacts: " + ex.getMessage(), ex);
                }
        }

        /**
         * Extracts a previously downloaded mappings.
         *
         * @param revision        a mappings revision.
         * @param gameVersion     a game version.
         * @param outputDirectory an output directory.
         * @throws IOException when extracting fails.
         */
        private void extractMappings(@Nonnull String revision, @Nonnull String gameVersion, @Nonnull Path outputDirectory) throws IOException {
                if (revision.contains("snapshot")) {
                        revision = revision.substring(9);
                } else if (revision.contains("stable")) {
                        revision = revision.substring(7);
                }

                final Path mappingPath = outputDirectory.resolve(gameVersion + "-" + revision + ".zip");
                final Path outputPath = outputDirectory.resolve("mcp");

                this.extractZip(mappingPath, outputPath);
        }

        /**
         * Extracts a zip file.
         *
         * @param inputFile       an input file.
         * @param outputDirectory an output directory.
         * @throws IOException when writing the file fails.
         */
        private void extractZip(@Nonnull Path inputFile, @Nonnull Path outputDirectory) throws IOException {
                Files.createDirectories(outputDirectory);

                try (ZipFile file = new ZipFile(inputFile.toFile())) {
                        Enumeration<? extends ZipEntry> entries = file.entries();

                        while (entries.hasMoreElements()) {
                                ZipEntry entry = entries.nextElement();
                                Path entryPath = outputDirectory.resolve(entry.getName());

                                if (entry.isDirectory()) {
                                        Files.createDirectories(entryPath);
                                        continue;
                                }

                                try (InputStream inputStream = file.getInputStream(entry)) {
                                        try (ReadableByteChannel inputChannel = Channels.newChannel(inputStream)) {
                                                try (FileChannel outputChannel = FileChannel.open(outputDirectory.resolve(entry.getName()), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                                                        outputChannel.transferFrom(inputChannel, 0, Long.MAX_VALUE);
                                                }
                                        }
                                }
                        }
                }
        }
}
