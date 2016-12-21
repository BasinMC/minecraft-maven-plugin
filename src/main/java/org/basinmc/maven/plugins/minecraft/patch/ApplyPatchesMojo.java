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
package org.basinmc.maven.plugins.minecraft.patch;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Applies a set of patches from the source directory.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "apply-patches",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.GENERATE_SOURCES
)
public class ApplyPatchesMojo extends AbstractGitCommandMojo {

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        this.getLog().info("Applying patches");

        if (Files.notExists(this.getPatchDirectory().toPath())) {
            this.getLog().warn("Skipping patching process - Patch directory does not exist");
            return;
        }

        // abort any pending merges before attempting anything else and reset the repository back
        // to its original state
        try {
            // Note: We are ignoring errors reported by git here since newer versions of git seem to
            // consider invoking git am --abort on an repository outside of an archive merging state
            // to be an error
            if (this.execute(new ProcessBuilder("git", "am", "--abort")) != 0) {
                this.getLog().info("No previous merge operation in process - Resuming operation");
            } else {
                this.getLog().warn("Aborted previous merge operation");
            }
        } catch (InterruptedException ex) {
            throw new MojoFailureException("Interrupted while awaiting git return status: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to invoke git: " + ex.getMessage(), ex);
        }

        if (this.getSettings().isInteractiveMode()) {
            this.getLog().info("Interactive mode is enabled - Conflicts will be reported and may pause the build process");
            this.getLog().info("Note: If you are running this build on a CI server, you should disable interactive mode in your maven configuration (or switch to batch mode using the corresponding command line argument)");
        }

        // apply patches one-by-one
        try {
            try {
                Files.walk(this.getPatchDirectory().toPath())
                        .filter((p) -> p.getFileName().toString().endsWith(".patch"))
                        .sorted()
                        .forEachOrdered((p) -> {
                            this.getLog().info("Applying " + p.toString());
                            List<String> command = new ArrayList<>(Arrays.asList("git", "am", "--ignore-whitespace", "--3way"));

                            if (this.getSettings().isInteractiveMode()) {
                                command.add("--reject");
                            }

                            command.add(this.getSourceDirectory().toPath().relativize(p.toAbsolutePath()).toString());

                            try {
                                if (this.execute(new ProcessBuilder(command).directory(this.getSourceDirectory())) != 0) {
                                    this.getLog().error("Could not apply patch from file " + p.toString());

                                    if (!this.getSettings().isInteractiveMode()) {
                                        this.getLog().error("Cannot recover from failure - Switch to interactive mode to resolve this issue");
                                        throw new MojoFailureException("Failed to apply patch " + p.toString());
                                    }

                                    this.getLog().info("Merge mode activated");
                                    this.getLog().info("Perform a manual merge for the modified files and confirm by entering \"Y\"");

                                    while (true) {
                                        this.getLog().info("Continue process?");
                                        this.getLog().info("Confirm Command: (Y)es / (N)o / (S)kip");

                                        int input = System.in.read();

                                        if (input == 'N' || input == 'n') {
                                            this.getLog().error("Did not solve merge error - Cannot recover from build failure");
                                            throw new MojoFailureException("Failed to apply patch " + p.toString());
                                        }

                                        if (input == 'S' || input == 's') {
                                            if (this.execute(new ProcessBuilder("git", "am", "--skip")) != 0) {
                                                throw new MojoFailureException("Git returned an unexpected error");
                                            }

                                            break;
                                        }

                                        if (input == 'Y' || input == 'y') {
                                            if (this.execute(new ProcessBuilder("git", "am", "--continue")) != 0) {
                                                throw new MojoFailureException("Git returned an unexpected error");
                                            }

                                            break;
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                throw new WrappedException(ex);
                            }
                        });
            } catch (WrappedException ex) {
                throw ex.getCause();
            }
        } catch (IOException ex) {
            throw new MojoFailureException("Cannot access one or more patch files: " + ex.getMessage(), ex);
        } catch (MojoFailureException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new MojoFailureException("Caught unexpected exception: " + ex.getMessage(), ex);
        }
    }

    /**
     * Provides a wrapper exception which acts as a transport through the boundaries of lambda
     * methods.
     */
    private static class WrappedException extends RuntimeException {
        public WrappedException(Throwable throwable) {
            super(throwable);
        }
    }
}
