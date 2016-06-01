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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;

/**
 * <strong>Git Safeguard Mojo</strong>
 *
 * Ensures git is available and the source directory is in a clean state in order to patch its contents.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "git-safeguard",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.VALIDATE
)
public class GitSafeguardMojo extends AbstractMinecraftMojo {

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoExecutionException, MojoFailureException {
                super.execute();

                this.verifyGitInstallation();
                this.verifyRepositoryState();
        }

        /**
         * Verifies the presence of git in the environment.
         *
         * @throws MojoFailureException when git cannot be found, the process is interrupted or a non-zero value is returned by git.
         */
        private void verifyGitInstallation() throws MojoFailureException {
                this.getLog().info("Searching for a valid git installation");

                try {
                        Process process = new ProcessBuilder().command("git", "--version").start();
                        int returnValue;

                        if ((returnValue = process.waitFor()) != 0) {
                                throw new IllegalStateException("Unexpected return value: " + returnValue);
                        }

                        try (InputStream inputStream = process.getInputStream()) {
                                try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                                        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
                                                String version = bufferedReader.readLine();
                                                this.getLog().info("Found git " + version + " in PATH");
                                        }
                                }
                        }
                } catch (IOException ex) {
                        throw new MojoFailureException("Could not verify git installation: " + ex.getMessage(), ex);
                } catch (InterruptedException ex) {
                        throw new MojoFailureException("Interrupted while awaiting git version string");
                } catch (IllegalStateException ex) {
                        throw new MojoFailureException("Git exited with an error: " + ex.getMessage());
                }
        }

        /**
         * Verifies the current repository state.
         *
         * @throws MojoFailureException when git cannot be found, the process is interrupted, the repository is in a dirty state or when a non-zero status code is returned.
         */
        private void verifyRepositoryState() throws MojoFailureException {
                this.getLog().info("Validating repository state ...");

                if (Files.notExists(this.sourceDirectory.toPath())) {
                        this.getLog().info("No repository found. Assuming clean repository state.");
                        return;
                }

                try {
                        Process process = new ProcessBuilder("git", "status", "--porcelain").directory(this.sourceDirectory).start();
                        int returnValue;

                        if ((returnValue = process.waitFor()) != 0) {
                                throw new IllegalStateException("Unexpected return value: " + returnValue);
                        }

                        try (InputStream inputStream = process.getInputStream()) {
                                try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                                        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
                                                String line = bufferedReader.readLine();

                                                if (line != null && !line.isEmpty()) {
                                                        this.getLog().error("The repository is currently in a dirty state.");
                                                        this.getLog().error("To prevent accidental overwriting of your changes, the execution was aborted.");
                                                        this.getLog().error("Please run org.basinmc.maven.plugins:minecraft-maven-plugin:generate-patches before attempting to compile this project again.");

                                                        throw new MojoFailureException("Repository is in an invalid state");
                                                }
                                        }
                                }
                        }
                } catch (IOException ex) {
                        throw new MojoFailureException("Could not execute git: " + ex.getMessage(), ex);
                } catch (InterruptedException ex) {
                        throw new MojoFailureException("Interrupted while awaiting git response");
                } catch (IllegalStateException ex) {
                        throw new MojoFailureException("Git exited with an error: " + ex.getMessage());
                }
        }
}
