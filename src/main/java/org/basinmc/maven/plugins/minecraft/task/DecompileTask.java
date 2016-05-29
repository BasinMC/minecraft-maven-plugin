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

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import org.apache.maven.plugin.MojoFailureException;
import org.basinmc.maven.plugins.minecraft.MinecraftMojo;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;
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
                Path outputDirectory = sourceOutputDirectory.resolve(module);
                Path inputPath = jarOutputDirectory.resolve(module + "_mapped.jar");
                Files.createDirectories(outputDirectory);

                Map<String, Object> options = new HashMap<>();
                options.put("dgs", 1);
                options.put("hdc", 0);
                options.put("rbr", 0);
                options.put("asc", 1);
                options.put("udv", 0);

                // Fixme: Slightly hacky .... .... blame sycholic
                MojoDecompiler decompiler = new MojoDecompiler(outputDirectory, new ZipFile(inputPath.toFile()));
                Fernflower fernflower = new Fernflower(decompiler, decompiler, options, new PrintStreamLogger(System.out));

                fernflower.getStructContext().addSpace(inputPath.toFile(), true);

                try {
                        fernflower.decompileContext();
                } finally {
                        fernflower.clearContext();
                }

                this.getMojo().getProject().addCompileSourceRoot(outputDirectory.toString());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute() throws MojoFailureException {
                try {
                        if (this.getMojo().getModules().contains("client")) {
                                this.decompile("client", this.getMojo().getJarOutputDirectory().toPath(), this.getMojo().getSourceOutputDirectory().toPath());
                        }

                        if (this.getMojo().getModules().contains("server")) {
                                this.decompile("server", this.getMojo().getJarOutputDirectory().toPath(), this.getMojo().getSourceOutputDirectory().toPath());
                        }
                } catch (IOException ex) {
                        throw new MojoFailureException("Cannot de-compile one or more files: " + ex.getMessage(), ex);
                }
        }

        /**
         * <strong>Mojo Decompiler</strong>
         *
         * Provides an extended version of Fernflower's console based decompiler.
         */
        class MojoDecompiler implements IBytecodeProvider, IResultSaver {
                private final ZipFile inputFile;
                private final Path outputPath;
                private final Formatter formatter = new Formatter();

                public MojoDecompiler(@Nonnull Path outputPath, @Nonnull ZipFile inputFile) {
                        this.outputPath = outputPath;
                        this.inputFile = inputFile;
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void closeArchive(String path, String archiveName) {
                        try {
                                this.inputFile.close();
                        } catch (IOException e) {
                                e.printStackTrace();
                        }
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void copyEntry(String source, String path, String archiveName, String entryName) {
                        ZipEntry entry = this.inputFile.getEntry(entryName);
                        Path outputPath = this.outputPath.resolve(path).resolve(entryName);

                        if (!entryName.startsWith("assets") && !entryName.startsWith("net") && !entryName.equals("log4j2.xml") && !entryName.equals("yggdrasil_session_pubkey.der") && !entry.getName().equals("pack.png")) {
                                return;
                        }

                        try {
                                Files.createDirectories(outputPath.getParent());

                                try (InputStream inputStream = this.inputFile.getInputStream(entry)) {
                                        try (ReadableByteChannel inputChannel = Channels.newChannel(inputStream)) {
                                                try (FileChannel outputChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                                                        outputChannel.transferFrom(inputChannel, 0, Long.MAX_VALUE);
                                                }
                                        }
                                }
                        } catch (IOException ex) {
                                String message = "Cannot copy entry " + entryName + " from " + source;
                                DecompilerContext.getLogger().writeMessage(message, ex);
                        }
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void copyFile(String source, String path, String entryName) {
                        try {
                                InterpreterUtil.copyFile(new File(source), this.outputPath.resolve(path).resolve(entryName).toFile());
                        } catch (IOException ex) {
                                DecompilerContext.getLogger().writeMessage("Cannot copy " + source + " to " + entryName, ex);
                        }
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void createArchive(String path, String archiveName, Manifest manifest) {
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
                        File file = new File(externalPath);

                        if (internalPath == null) {
                                return InterpreterUtil.getBytes(file);
                        } else {
                                ZipEntry entry = this.inputFile.getEntry(internalPath);
                                if (entry == null) throw new IOException("Entry not found: " + internalPath);

                                return InterpreterUtil.getBytes(this.inputFile, entry);
                        }
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
                        this.saveClassFile(path, qualifiedName, entryName, content, new int[0]);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
                        Path entryPath = this.outputPath.resolve(path).resolve(entryName);

                        if (!qualifiedName.startsWith("net")) {
                                return;
                        }

                        try {
                                content = this.formatter.formatSource(content);
                        } catch (FormatterException ex) {
                                DecompilerContext.getLogger().writeMessage("Cannot format file " + entryName, ex);
                        }

                        try {
                                Files.createDirectories(entryPath.getParent());

                                try (FileChannel outputChannel = FileChannel.open(entryPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                                        outputChannel.write(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8))); // TODO: Maybe configure the character set?
                                }
                        } catch (IOException ex) {
                                DecompilerContext.getLogger().writeMessage("Cannot write class file " + entryName, ex);
                        }
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void saveDirEntry(String path, String archiveName, String entryName) {
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void saveFolder(String path) {
                        try {
                                Files.createDirectories(this.outputPath.resolve(path));
                        } catch (IOException ex) {
                                throw new RuntimeException("Cannot create directory: " + ex.getMessage(), ex);
                        }
                }
        }
}
