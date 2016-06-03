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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;
import java.nio.file.Files;

/**
 * <strong>Generate Patches Mojo</strong>
 *
 * Re-generates all patches based on modifications applied to the source code.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "generate-patches",
        requiresProject = false,
        threadSafe = true
)
public class GeneratePatchesMojo extends AbstractMinecraftMojo {

        /**
         * Deletes all patches from the patch directory.
         *
         * @throws MojoFailureException when deleting one or more patch files fails.
         */
        private void clearPatches() throws MojoFailureException {
                this.getLog().info("Clearing patch directory");

                try {
                        if (Files.notExists(this.patchDirectory.toPath())) {
                                Files.createDirectories(this.patchDirectory.toPath());
                        } else {
                                Files.walk(this.patchDirectory.toPath()).filter((p) -> p.getFileName().endsWith(".patch"))
                                        .forEach((p) -> {
                                                try {
                                                        Files.delete(p);
                                                } catch (IOException ex) {
                                                        throw new RuntimeException("Could not delete file: " + ex.getMessage(), ex);
                                                }
                                        });
                        }
                } catch (IOException | RuntimeException ex) {
                        throw new MojoFailureException("Could not clear patches: " + ex.getMessage(), ex);
                }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoExecutionException, MojoFailureException {
                super.execute();

                this.clearPatches();
                this.generatePatches();
        }

        /**
         * Generates a new set of patches.
         *
         * @throws MojoFailureException when generating the patches fails.
         */
        private void generatePatches() throws MojoFailureException {
                this.getLog().info("Generating patch files ...");

                int statusCode;
                if ((statusCode = this.executeCommand(new ProcessBuilder().command("git", "format-patch", "--minimal", "--no-stat", "-N", "-o", this.patchDirectory.toString(), "upstream").directory(this.sourceDirectory))) != 0) {
                        throw new MojoFailureException("Git returned an unexpected status code: " + statusCode);
                }
        }
}
