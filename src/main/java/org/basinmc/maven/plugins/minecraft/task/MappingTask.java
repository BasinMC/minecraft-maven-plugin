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

import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.SpecialSource;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.md_5.specialsource.transformer.MinecraftCodersPack;
import org.apache.maven.plugin.MojoFailureException;
import org.basinmc.maven.plugins.minecraft.MinecraftMojo;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

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
                Path fieldMappingPath = mcpPath.resolve("fields.csv");
                Path methodMappingPath = mcpPath.resolve("methods.csv");

                Path filePath = this.getMojo().getJarOutputDirectory().toPath().resolve(side + ".jar");
                Path outputPath = this.getMojo().getJarOutputDirectory().toPath().resolve(side + "_mapped.jar");

                SpecialSource.kill_lvt = true;
                SpecialSource.kill_source = true;

                MinecraftCodersPack outputTransformer = new MinecraftCodersPack(fieldMappingPath.toFile(), methodMappingPath.toFile(), null);

                JarMapping mapping = new JarMapping();
                mapping.loadMappings(new BufferedReader(new FileReader(srgPath.resolve("joined.csrg").toFile())), null, outputTransformer, false);

                JarRemapper remapper = new JarRemapper(mapping);

                Jar jar = Jar.init(filePath.toFile());

                JointProvider inheritanceProvider = new JointProvider();
                inheritanceProvider.add(new JarProvider(jar));
                mapping.setFallbackInheritanceProvider(inheritanceProvider);

                remapper.remapJar(jar, outputPath.toFile());
        }
}
