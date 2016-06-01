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

import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * <strong>Download Minecraft Mojo</strong>
 *
 * Downloads a Minecraft executable from the official servers.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "download",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.INITIALIZE
)
public class DownloadMinecraftMojo extends AbstractMinecraftMojo {
        public static final String CLIENT_JAR_LOCATION_TEMPLATE = "https://s3.amazonaws.com/Minecraft.Download/versions/%1$s/%1$s.jar";
        public static final String SERVER_JAR_LOCATION_TEMPLATE = "https://s3.amazonaws.com/Minecraft.Download/versions/%1$s/minecraft_server.%1$s.jar";

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoExecutionException, MojoFailureException {
                super.execute();

                try {
                        if (!this.locateArtifact(this.module).isPresent()) {
                                this.getLog().info("No cached version of net:minecraft:" + this.module + ":" + this.gameVersion + " found in local repository");

                                Path artifactPath = Files.createTempFile("minecraft_" + this.module, ".jar");
                                Path pomPath = Files.createTempFile("minecraft_" + this.module, ".xml");

                                final String url;

                                if (this.module.equalsIgnoreCase("client")) {
                                        url = String.format(CLIENT_JAR_LOCATION_TEMPLATE, this.gameVersion);
                                } else {
                                        url = String.format(SERVER_JAR_LOCATION_TEMPLATE, this.gameVersion);
                                }

                                this.downloadArtifact(artifactPath, url);
                                this.generateArtifactDescriptor(pomPath, this.module);
                                this.installArtifact(this.module, pomPath, artifactPath);
                        } else {
                                this.getLog().info("Found cached version of " + MINECRAFT_GROUP_ID + ":" + this.module + ":" + this.gameVersion);
                        }
                } catch (IOException ex) {
                        throw new MojoFailureException("Could not create temporary file: " + ex.getMessage(), ex);
                } catch (ArtifactResolutionException ex) {
                        throw new MojoFailureException("Could not resolve vanilla artifact: " + ex.getMessage(), ex);
                }
        }
}
