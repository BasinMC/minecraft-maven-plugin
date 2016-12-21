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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Provides a Mojo capable of re-generating patches based on the unmodified module code base.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "generate-patches",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES
)
public class GeneratePatchesMojo extends AbstractGitCommandMojo {

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        this.getLog().info("Purging previous patch files");

        try {
            Files.walk(this.getPatchDirectory().toPath())
                    .filter((p) -> p.getFileName().toString().endsWith(".patch"))
                    .forEach((p) -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ex) {
                            throw new WrappedException(ex);
                        }
                    });
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to access patch directory: " + ex.getMessage(), ex);
        } catch (WrappedException ex) {
            if (ex.getCause() instanceof IOException) {
                throw new MojoFailureException("Failed to delete patch file: " + ex.getMessage(), ex);
            }

            throw new MojoFailureException("Caught unexpected exception: " + ex.getMessage(), ex);
        }

        try {
            Repository repository = new FileRepositoryBuilder()
                    .setWorkTree(this.getSourceDirectory())
                    .setMustExist(true)
                    .build();

            Git git = new Git(repository);

            if (!git.status().call().isClean()) {
                this.getLog().warn("One or more uncommited changes present within source directory\n");
                this.getLog().warn("Only commited changes will be considered in patch generation");
            }
        } catch (GitAPIException ex) {
            throw new MojoFailureException("Failed to invoke git: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Could not access module repository: " + ex.getMessage(), ex);
        }

        Path workingDirectory = this.getSourceDirectory().toPath().toAbsolutePath();
        Path relativePatchDirectory = workingDirectory.relativize(this.getPatchDirectory().toPath());

        try {
            if (this.execute(new ProcessBuilder().command("git", "format-patch", "--minimal", "--no-stat", "-N", "-o", relativePatchDirectory.toString(), "upstream").directory(this.getSourceDirectory().getAbsoluteFile())) != 0) {
                throw new MojoFailureException("Git reported an unexpected error");
            }
        } catch (InterruptedException ex) {
            throw new MojoFailureException("Interrupted while awaiting git result: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to invoke git: " + ex.getMessage(), ex);
        }
    }

    private static class WrappedException extends RuntimeException {
        public WrappedException(Throwable throwable) {
            super(throwable);
        }
    }
}
