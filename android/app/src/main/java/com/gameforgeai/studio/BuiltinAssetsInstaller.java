package com.gameforgeai.studio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs the Minefade source snapshot and the default local model without user import steps. */
public final class BuiltinAssetsInstaller {
    public static final String MODEL_ASSET =
            "models/qwen2.5-coder-0.5b-instruct-q3_k_m.gguf";
    public static final String MODEL_FILE =
            "qwen2.5-coder-0.5b-instruct-q3_k_m.gguf";
    private static final long MIN_MODEL_BYTES = 300L * 1024L * 1024L;
    private static final long MAX_MODEL_BYTES = 400L * 1024L * 1024L;
    private static final long MAX_PROJECT_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_PROJECT_FILES = 9000;

    private BuiltinAssetsInstaller() { }

    public static File ensureMinefadeProject(Context context, File filesDir) throws IOException {
        File projects = new File(filesDir, "projects");
        if (!projects.exists() && !projects.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta de proyectos.");
        }
        File destination = new File(projects, "minefade");
        File marker = new File(destination, "gameforge-project.json");
        if (marker.isFile() && new File(destination, "app/src/main/cpp").isDirectory()
                && new File(destination, "README.md").isFile()) {
            return destination;
        }
        if (destination.exists()) {
            throw new IOException("El proyecto Minefade existe, pero está incompleto. No se sobrescribió para proteger tus archivos.");
        }

        File temporary = new File(projects, ".minefade-install-" + UUID.randomUUID());
        if (!temporary.mkdirs()) throw new IOException("No se pudo preparar la instalación del proyecto Minefade.");
        boolean installed = false;
        try (InputStream raw = context.getAssets().open("builtin/minefade-source.zip");
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            String tempRoot = temporary.getCanonicalPath() + File.separator;
            ZipEntry entry;
            long totalBytes = 0L;
            int fileCount = 0;
            byte[] buffer = new byte[32 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.isEmpty() || name.startsWith("/") || name.contains("\u0000")) {
                    throw new IOException("El paquete Minefade contiene una ruta no válida.");
                }
                File target = new File(temporary, name).getCanonicalFile();
                if (!target.getPath().startsWith(tempRoot)) {
                    throw new IOException("El paquete Minefade intenta salir de su carpeta.");
                }
                if (entry.isDirectory()) {
                    if (!target.exists() && !target.mkdirs()) throw new IOException("No se pudo crear una carpeta del proyecto.");
                } else {
                    if (++fileCount > MAX_PROJECT_FILES) throw new IOException("El paquete contiene demasiados archivos.");
                    File parent = target.getParentFile();
                    if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                        throw new IOException("No se pudo preparar un archivo del proyecto.");
                    }
                    try (FileOutputStream output = new FileOutputStream(target, false)) {
                        int count;
                        long entryBytes = 0L;
                        while ((count = zip.read(buffer)) != -1) {
                            entryBytes += count;
                            totalBytes += count;
                            if (entryBytes > 12L * 1024L * 1024L || totalBytes > MAX_PROJECT_BYTES) {
                                throw new IOException("El paquete Minefade supera el tamaño de seguridad permitido.");
                            }
                            output.write(buffer, 0, count);
                        }
                        output.getFD().sync();
                    }
                }
                zip.closeEntry();
            }
            File sourceManifest = new File(temporary, "gameforge-bundle.json");
            if (!new File(temporary, "README.md").isFile()
                    || !new File(temporary, "app/src/main/cpp").isDirectory()
                    || !sourceManifest.isFile()) {
                throw new IOException("El paquete incluido no contiene el código fuente esperado de Minefade.");
            }
            JSONObject bundle = new JSONObject(readSmallText(sourceManifest));
            JSONObject project = new JSONObject();
            project.put("id", "minefade");
            project.put("name", "Minefade");
            project.put("template", "minefade_native");
            project.put("createdAt", System.currentTimeMillis());
            project.put("description", "Proyecto nativo Minefade 2.0 incluido en GameForge AI.");
            project.put("sourceRepository", bundle.optString("repository"));
            project.put("sourceCommit", bundle.optString("commit"));
            project.put("sourceVersion", bundle.optString("version"));
            // Write the marker inside the staging directory before the atomic install.
            File temporaryMarker = new File(temporary, "gameforge-project.json");
            writeSynced(temporaryMarker, project.toString(2));
            try {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary.toPath(), destination.toPath());
            }
            installed = true;
            return destination;
        } catch (Exception error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("No se pudo instalar el código Minefade incluido.", error);
        } finally {
            if (!installed) deleteTree(temporary);
        }
    }

    public static File ensureBundledModel(Context context, File filesDir) throws IOException {
        File models = new File(filesDir, "models");
        if (!models.exists() && !models.mkdirs()) {
            throw new IOException("No se pudo crear la biblioteca local de IA.");
        }
        File destination = new File(models, MODEL_FILE);
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(MODEL_ASSET)) {
            long length = descriptor.getLength();
            if (length < MIN_MODEL_BYTES || length > MAX_MODEL_BYTES) {
                throw new IOException("El modelo incluido no tiene un tamaño válido.");
            }
            String expectedHash = readExpectedModelHash(context);
            if (destination.isFile() && destination.length() == length
                    && expectedHash.equals(sha256(destination))) {
                return destination;
            }
            long required = length + 96L * 1024L * 1024L;
            if (models.getUsableSpace() < required) {
                throw new IOException("No hay espacio suficiente para instalar automáticamente el modelo de IA. Libera al menos "
                        + (required / (1024L * 1024L)) + " MB.");
            }

            File temporary = new File(models, MODEL_FILE + ".copying");
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (Exception error) { throw new IOException("No se pudo inicializar la comprobación del modelo.", error); }

            try (InputStream input = new BufferedInputStream(descriptor.createInputStream());
                 FileOutputStream file = new FileOutputStream(temporary, false);
                 BufferedOutputStream output = new BufferedOutputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int count;
                long copied = 0L;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    copied += count;
                }
                output.flush();
                file.getFD().sync();
                if (copied != length || copied < MIN_MODEL_BYTES) {
                    throw new IOException("La copia del modelo quedó incompleta.");
                }
            } catch (Exception error) {
                temporary.delete();
                if (error instanceof IOException) throw (IOException) error;
                throw new IOException("Falló la copia del modelo local.", error);
            }
            String hash = toHex(digest.digest());
            if (!expectedHash.equals(hash)) {
                temporary.delete();
                throw new IOException("El hash SHA-256 del modelo incluido no coincide con su manifiesto.");
            }
            try {
                Files.move(temporary.toPath(), destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            File checksum = new File(models, MODEL_FILE + ".sha256");
            writeSynced(checksum, hash + "\n");
            return destination;
        }
    }

    private static String readExpectedModelHash(Context context) throws IOException {
        try (InputStream input = context.getAssets().open("models/model-info.json")) {
            byte[] bytes = new byte[64 * 1024];
            int count = input.read(bytes);
            if (count <= 0) throw new IOException("El manifiesto del modelo está vacío.");
            JSONObject info = new JSONObject(new String(bytes, 0, count, StandardCharsets.UTF_8));
            String hash = info.optString("sha256", "");
            if (!hash.matches("[a-fA-F0-9]{64}")) throw new IOException("El manifiesto del modelo no contiene un SHA-256 válido.");
            return hash.toLowerCase(java.util.Locale.ROOT);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("No se pudo leer el manifiesto del modelo incluido.", error);
        }
    }

    public static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[1024 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return toHex(digest.digest());
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("No se pudo verificar el modelo.", error);
        }
    }

    private static String readSmallText(File file) throws IOException {
        if (file.length() > 64 * 1024) throw new IOException("El manifiesto del paquete es demasiado grande.");
        try (InputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = input.read(bytes);
            return new String(bytes, 0, Math.max(0, read), StandardCharsets.UTF_8);
        }
    }

    private static void writeSynced(File file, String value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static void deleteTree(File target) {
        if (target == null || !target.exists()) return;
        File[] children = target.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        target.delete();
    }
}
