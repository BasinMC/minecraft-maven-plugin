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
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * <strong>Abstract Mojo</strong>
 *
 * Provides a base mojo implementation which provides access to certain configuration resources.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public abstract class AbstractMinecraftMojo extends AbstractMojo {

        /**
         * Specifies the game version to download and map.
         */
        @Parameter(defaultValue = "1.9.4")
        protected String gameVersion;
        /**
         * Specifies a list of modules to download decompile and remap.
         * Valid values are: client, server
         */
        @Parameter(defaultValue = "server")
        protected String module;
        /**
         * Stores a reference to the project's configuration.
         */
        @Parameter(property = "project", required = true, readonly = true)
        protected MavenProject project;

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoExecutionException, MojoFailureException {
                if (!this.module.equalsIgnoreCase("client") && !this.module.equalsIgnoreCase("server")) {
                        throw new MojoFailureException("Invalid module name \"" + module + "\"");
                }
        }
}
