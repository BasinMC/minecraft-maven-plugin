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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Files;

/**
 * <strong>Git Patch Mojo</strong>
 *
 * Applies a mail archive of patches to the generated sources in order to fix compilation errors and alter the way
 * a Minecraft server or client behaves.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "git-patch",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.PROCESS_SOURCES
)
public class GitPatchMojo extends AbstractMinecraftMojo {

        /**
         * Aborts an unfinished merge operation in the source directory.
         *
         * @throws MojoFailureException when an error occurs while executing git.
         */
        private void abortMerge() throws MojoFailureException {
                this.getLog().info("Aborting any unfinished merge operations");
                int statusCode;

                // Note: This is one of the only commands which aren't supported by JGit and thus we will need to rely
                // on the git executable in the user's environment
                if ((statusCode = this.executeCommand(new ProcessBuilder().command("git", "am", "--abort").directory(this.sourceDirectory))) != 0) {
                        throw new MojoFailureException("Git returned an unexpected status: " + statusCode);
                }
        }

        /**
         * Applies a set of patches to the source code.
         *
         * @throws MojoFailureException when patching fails.
         */
        private void applyPatches() throws MojoFailureException {
                this.getLog().info("Applying all known patches");
                int statusCode;

                try {
                        if (Files.walk(this.sourceDirectory.toPath(), 1).filter((p) -> p.getFileName().toString().endsWith(".patch")).count() == 0) {
                                this.getLog().warn("No patch files found - Skipping patch phase");
                                return;
                        }
                } catch (IOException ex) {
                        throw new MojoFailureException("Could not inspect patch directory: " + ex.getMessage(), ex);
                }

                String patchPattern = this.patchDirectory.toString();

                if (!patchPattern.endsWith("/")) {
                        patchPattern += "/";
                }

                patchPattern += "*.patch";

                // Note: This is one of the only commands which aren't supported by JGit and thus we will need to rely
                // on the git executable in the user's environment
                if ((statusCode = this.executeCommand(new ProcessBuilder().command("git", "am", "--3way", patchPattern).directory(this.sourceDirectory))) != 0) {
                        throw new MojoFailureException("Git returned an unexpected status: " + statusCode);
                }
        }

        /**
         * Creates a new repository and adds all current sources to it in order to apply patches.
         *
         * @throws MojoFailureException when an error occurs while executing git.
         */
        private void createRepository() throws MojoFailureException {
                final Git git;

                try {
                        this.getLog().info("Initializing fresh repository");
                        InitCommand command = new InitCommand();
                        command.setDirectory(this.sourceDirectory);
                        git = command.call();
                } catch (GitAPIException ex) {
                        throw new MojoFailureException("Could not initialize repository: " + ex.getMessage(), ex);
                }

                try {
                        this.getLog().info("Adding all sources to the repository");
                        git.add().addFilepattern("*.java").call();
                } catch (GitAPIException ex) {
                        throw new MojoFailureException("Could not add vanilla source to the repository: " + ex.getMessage(), ex);
                }

                try {
                        this.getLog().info("Committing changes");
                        git.commit().setMessage("Added decompiled sources.").call();
                } catch (GitAPIException ex) {
                        throw new MojoFailureException("Could not commit changes: " + ex.getMessage(), ex);
                }

                try {
                        this.getLog().info("Creating an upstream branch to compare against");
                        git.branchCreate().setName("upstream").call();
                } catch (GitAPIException ex) {
                        throw new MojoFailureException("Could not create branch: " + ex.getMessage(), ex);
                }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoExecutionException, MojoFailureException {
                super.execute();

                if (Files.notExists(this.sourceDirectory.toPath().resolve(".git"))) {
                        this.createRepository();
                } else {
                        this.abortMerge();
                }

                this.applyPatches();
        }
}