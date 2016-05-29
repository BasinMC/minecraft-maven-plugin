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

import com.google.common.io.ByteStreams;
import org.apache.maven.plugin.MojoFailureException;
import org.basinmc.maven.plugins.minecraft.MinecraftMojo;
import org.basinmc.maven.plugins.minecraft.task.mapping.McpMapping;
import org.basinmc.maven.plugins.minecraft.task.mapping.SrgMapping;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import javax.annotation.Nonnull;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * <strong>Mapping Task</strong>
 *
 * Attempts to receive a specific set of Minecraft mappings or the latest compatible version depending on the Mojo
 * configuration.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public class MappingTask extends AbstractTask {

        public MappingTask(@Nonnull MinecraftMojo mojo) {
                super(mojo);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoFailureException {
                try {
                        if (this.getMojo().getModules().contains("client")) {
                                this.getLog().info("Remapping client module.");
                                this.mapArtifact("client", this.getMojo().getMappingOutputDirectory().toPath().resolve("srg"), this.getMojo().getMappingOutputDirectory().toPath().resolve("mcp"));
                        }

                        if (this.getMojo().getModules().contains("server")) {
                                this.getLog().info("Remapping server module.");
                                this.mapArtifact("server", this.getMojo().getMappingOutputDirectory().toPath().resolve("srg"), this.getMojo().getMappingOutputDirectory().toPath().resolve("mcp"));
                        }

                        this.getLog().info("Finalized remapping.");
                } catch (IOException ex) {
                        throw new MojoFailureException("Cannot remap one or more files: " + ex.getMessage(), ex);
                }
        }

        /**
         * Re-Maps a single artifact using the SRG and MCP mappings.
         *
         * @param side    a side (such as server or client).
         * @param srgPath a searge mapping path.
         * @param mcpPath an mcp mapping path.
         * @throws IOException when remapping fails.
         */
        private void mapArtifact(@Nonnull String side, @Nonnull Path srgPath, @Nonnull Path mcpPath) throws IOException {
                SrgMapping srgMapping = new SrgMapping(srgPath.resolve("joined.csrg"));
                McpMapping mcpMapping = new McpMapping(side, mcpPath.resolve("fields.csv"), mcpPath.resolve("methods.csv"));

                Path filePath = this.getMojo().getJarOutputDirectory().toPath().resolve(side + ".jar");
                Path outputPath = this.getMojo().getJarOutputDirectory().toPath().resolve(side + "_mapped.jar");

                try (FileInputStream fileInputStream = new FileInputStream(filePath.toFile())) {
                        try (ZipInputStream inputStream = new ZipInputStream(fileInputStream)) {
                                try (FileOutputStream fileOutputStream = new FileOutputStream(outputPath.toFile())) {
                                        try (ZipOutputStream outputStream = new ZipOutputStream(fileOutputStream)) {
                                                ZipEntry entry;

                                                while ((entry = inputStream.getNextEntry()) != null) {
                                                        if (entry.isDirectory()) {
                                                                if (this.getLog().isDebugEnabled()) {
                                                                        this.getLog().debug("Creating directory " + entry.getName());
                                                                }

                                                                outputStream.putNextEntry(entry);
                                                                continue;
                                                        }

                                                        if (entry.getName().toUpperCase().endsWith(".RSA") || entry.getName().toUpperCase().endsWith(".SF")) {
                                                                if (this.getLog().isDebugEnabled()) {
                                                                        this.getLog().debug("Skipping signature file " + entry.getName());
                                                                }

                                                                continue;
                                                        }

                                                        if (!entry.getName().endsWith(".class")) {
                                                                if (this.getLog().isDebugEnabled()) {
                                                                        this.getLog().debug("Copying resource " + entry.getName());
                                                                }

                                                                outputStream.putNextEntry(entry);
                                                                ByteStreams.copy(inputStream, outputStream);
                                                                continue;
                                                        }

                                                        this.getLog().info("Remapping " + entry.getName());
                                                        ZipEntry outputEntry = new ZipEntry(srgMapping.mapType(entry.getName().substring(0, entry.getName().lastIndexOf('.'))) + ".class");

                                                        outputStream.putNextEntry(outputEntry);
                                                        this.mapClass(inputStream, outputStream, srgMapping, mcpMapping);
                                                }
                                        }
                                }
                        }
                }
        }

        /**
         * Maps a single class.
         *
         * @param inputStream  an input stream.
         * @param outputStream an output stream.
         * @param srgMapping   an SRG mapping.
         * @param mcpMapping   an MCP mapping.
         * @throws IOException when remapping the class fails.
         */
        public void mapClass(@Nonnull InputStream inputStream, @Nonnull ZipOutputStream outputStream, @Nonnull SrgMapping srgMapping, @Nonnull McpMapping mcpMapping) throws IOException {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                ClassReader reader = new ClassReader(inputStream);

                ClassRemapper mcpRemapper = new ClassRemapper(writer, mcpMapping);
                ClassRemapper srgRemapper = new ClassRemapper(mcpRemapper, srgMapping);

                reader.accept(srgRemapper, ClassReader.SKIP_DEBUG | ClassReader.EXPAND_FRAMES);

                outputStream.write(writer.toByteArray());
        }
}
