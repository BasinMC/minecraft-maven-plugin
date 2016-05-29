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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.basinmc.maven.plugins.minecraft.task.DecompileTask;
import org.basinmc.maven.plugins.minecraft.task.DownloadTask;
import org.basinmc.maven.plugins.minecraft.task.MappingTask;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * <strong>Minecraft Mojo</strong>
 *
 * Downloads, remaps and decompiles the Minecraft server and/or client to be able to apply custom modifications to the
 * codebase.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "minecraft",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES
)
public class MinecraftMojo extends AbstractMojo {

        /**
         * Stores a reference to the project's configuration.
         */
        @Parameter(property = "project", required = true, readonly = true)
        private MavenProject project;

        /**
         * specify grammar file encoding; e.g., euc-jp
         */
        @Parameter(property = "project.build.sourceEncoding")
        protected String encoding;

        /**
         * Specifies a list of modules to download decompile and remap.
         * Valid values are: client, server
         */
        @Parameter
        private List<String> modules = Arrays.asList("server");

        /**
         * Specifies the game version to download and map.
         */
        @Parameter(defaultValue = "1.9.4")
        private String gameVersion;

        /**
         * Specifies the mapping version to apply.
         * Note: Versions are prefixed with either snapshot or stable-.
         *
         * For more information check the <a href="http://export.mcpbot.bspk.rs/">MCP Bot website</a>.
         */
        @Parameter(defaultValue = "snapshot-20160510")
        private String mappingVersion;

        /**
         * Declares the directory Minecraft jars are written to.
         */
        @Parameter(defaultValue = "${project.build.directory}")
        private File jarOutputDirectory;

        /**
         * Declares the directory obfuscation mappings are written to.
         */
        @Parameter(defaultValue = "${project.build.directory}/mappings/")
        private File mappingOutputDirectory;

        /**
         * Declares the directory the generated sources are written to.
         */
        @Parameter(defaultValue = "${project.basedir}/src/minecraft/")
        private File sourceOutputDirectory;

        /**
         * Declares the directory which contains a set of changes to apply to the original Minecraft sources.
         */
        @Parameter(defaultValue = "${project.basedir}/src/minecraft/patches")
        private File patchDirectory;

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoExecutionException, MojoFailureException {
                this.getLog().info("Minecraft Version: " + this.gameVersion);
                this.getLog().info("Mapping Version: " + this.mappingVersion);

                (new DownloadTask(this)).execute();
                (new MappingTask(this)).execute();
                (new DecompileTask(this)).execute();
        }

        @Nonnull
        public MavenProject getProject() {
                return project;
        }

        @Nonnull
        public String getEncoding() {
                return this.encoding;
        }

        @Nonnull
        public List<String> getModules() {
                return modules;
        }

        @Nonnull
        public String getGameVersion() {
                return gameVersion;
        }

        @Nonnull
        public String getMappingVersion() {
                return mappingVersion;
        }

        @Nonnull
        public File getJarOutputDirectory() {
                return jarOutputDirectory;
        }

        @Nonnull
        public File getMappingOutputDirectory() {
                return mappingOutputDirectory;
        }

        @Nonnull
        public File getSourceOutputDirectory() {
                return sourceOutputDirectory;
        }

        @Nonnull
        public File getPatchDirectory() {
                return patchDirectory;
        }
}
