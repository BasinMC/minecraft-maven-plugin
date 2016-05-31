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

import org.apache.maven.plugin.MojoFailureException;
import org.basinmc.maven.plugins.minecraft.MinecraftMojo;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * <strong>Decompiler Task</strong>
 *
 * Decompiles previously downloaded modules.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public class DecompileTask extends AbstractTask {

        public DecompileTask(@Nonnull MinecraftMojo mojo) {
                super(mojo);
        }

        /**
         * Decompiles a complete module.
         *
         * @param module                a module name.
         * @param jarOutputDirectory    a jar output directory.
         * @param sourceOutputDirectory a source output directory.
         * @throws IOException when an error occurs.
         */
        private void decompile(@Nonnull String module, @Nonnull Path jarOutputDirectory, @Nonnull Path sourceOutputDirectory) throws IOException {
                this.getLog().info("Decompiling " + module + " ...");

                Path outputDirectory = sourceOutputDirectory.resolve(module);
                Path inputPath = jarOutputDirectory.resolve(module + "_mapped.jar");
                Files.createDirectories(outputDirectory);

                ConsoleDecompiler.main(new String[] { "-din=1", "-rbr=0", "-rsy=1", "-dgs=1", "-asc=1", "-log=ERROR", inputPath.toAbsolutePath().toString(), outputDirectory.toAbsolutePath().toString() });

                try (ZipFile file = new ZipFile(outputDirectory.resolve(module + "_mapped.jar").toFile())) {
                        Enumeration<? extends ZipEntry> enumeration = file.entries();

                        while (enumeration.hasMoreElements()) {
                                ZipEntry entry = enumeration.nextElement();
                                Path filePath = outputDirectory.resolve(entry.getName());

                                if (!entry.getName().startsWith("assets") && !entry.getName().startsWith("net") && !entry.getName().startsWith("log4j2.xml") && !entry.getName().startsWith("yggdrasil_session_pubkey.der")) {
                                        continue;
                                }

                                if (entry.isDirectory()) {
                                        Files.createDirectories(filePath);
                                        continue;
                                }

                                Files.createDirectories(filePath.getParent());

                                try (InputStream inputStream = file.getInputStream(entry)) {
                                        try (ReadableByteChannel inputChannel = Channels.newChannel(inputStream)) {
                                                try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                                                        fileChannel.transferFrom(inputChannel, 0, Long.MAX_VALUE);
                                                }
                                        }
                                }
                        }
                }

                Files.deleteIfExists(outputDirectory.resolve(module + "_mapped.jar"));
                this.getMojo().getProject().addCompileSourceRoot(outputDirectory.toString());

                this.getLog().info("Decompiled " + module);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoFailureException {
                try {
                        this.decompile(this.getMojo().getModule(), this.getMojo().getJarOutputDirectory().toPath(), this.getMojo().getSourceOutputDirectory().toPath());
                } catch (IOException ex) {
                        throw new MojoFailureException("Cannot de-compile one or more files: " + ex.getMessage(), ex);
                }
        }
}
