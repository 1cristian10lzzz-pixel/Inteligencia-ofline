package com.gameforgeai.studio;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Restricted local project tools for the offline coding agent.
 * Changes are staged as previews, applied only after explicit user approval,
 * backed up outside the project, and reversible through a small undo journal.
 */
public final class ProjectWorkspace {
    private static final int MAX_FILE_BYTES = 128 * 1024;
    private static final int MAX_CONTEXT_CHARS = 3200;
    private static final int MAX_LISTED_FILES = 120;
    private static final int MAX_BACKUPS = 12;

    private final File projectsRoot;
    private final File backupsRoot;
    private final Map<String, PendingEdit> pendingEdits = new HashMap<>();

    public ProjectWorkspace(File filesDir) {
        this.projectsRoot = new File(filesDir, "projects");
        this.backupsRoot = new File(filesDir, "project-backups");
    }

    private static final class PendingEdit {
        final String projectId;
        final String relativePath;
        final String oldHash;
        final String newContent;
        final String summary;
        final boolean fileExisted;

        PendingEdit(String projectId, String relativePath, String oldHash,
                    String newContent, String summary, boolean fileExisted) {
            this.projectId = projectId;
            this.relativePath = relativePath;
            this.oldHash = oldHash;
            this.newContent = newContent;
            this.summary = summary;
            this.fileExisted = fileExisted;
        }
    }

    public synchronized JSONObject listFiles(String projectId) {
        JSONObject result = new JSONObject();
        try {
            File root = projectRoot(projectId);
            JSONArray files = new JSONArray();
            collectFiles(root, root, files, 0);
            result.put("ok", true);
            result.put("files", files);
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    public synchronized JSONObject readFile(String projectId, String relativePath) {
        JSONObject result = new JSONObject();
        try {
            File root = projectRoot(projectId);
            File file = resolveProjectFile(root, relativePath, false);
            if (!file.isFile()) throw new IOException("No existe un archivo en esa ruta.");
            result.put("ok", true);
            result.put("path", normalizePath(relativePath));
            result.put("content", readLimited(file));
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    /** Creates a pending proposal; it does not write to the project. */
    public synchronized JSONObject previewEdit(String projectId, String proposalJson) {
        JSONObject result = new JSONObject();
        try {
            JSONObject proposal = new JSONObject(proposalJson == null ? "{}" : proposalJson);
            if (!"edit_file".equals(proposal.optString("action"))) {
                throw new IOException("La acción propuesta no está permitida.");
            }
            String relativePath = normalizePath(proposal.optString("path", ""));
            String summary = proposal.optString("summary", "Cambio propuesto por la IA").trim();
            String content = proposal.optString("content", "");
            if (content.length() > MAX_FILE_BYTES || content.indexOf('\0') >= 0) {
                throw new IOException("El archivo propuesto supera el límite de 128 KB o contiene datos no válidos.");
            }

            File root = projectRoot(projectId);
            File file = resolveProjectFile(root, relativePath, true);
            boolean exists = file.exists();
            if (exists && !file.isFile()) throw new IOException("La ruta seleccionada no es un archivo.");
            if (!exists && !relativePath.toLowerCase(Locale.ROOT).startsWith("scripts/")) {
                throw new IOException("Solo se permite crear archivos nuevos dentro de scripts/.");
            }
            if (!exists && (file.getParentFile() == null || !file.getParentFile().isDirectory())) {
                throw new IOException("La carpeta del nuevo archivo no existe.");
            }

            String previous = exists ? readLimited(file) : "";
            String previewId = UUID.randomUUID().toString();
            pendingEdits.put(previewId, new PendingEdit(
                    projectId, relativePath, sha256(previous), content, summary, exists));

            result.put("ok", true);
            result.put("previewId", previewId);
            result.put("projectId", projectId);
            result.put("path", relativePath);
            result.put("summary", summary);
            result.put("before", previous);
            result.put("after", content);
            result.put("createdNewFile", !exists);
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    /** Applies a reviewed preview only if the source has not changed since preview. */
    public synchronized JSONObject applyEdit(String previewId) {
        JSONObject result = new JSONObject();
        try {
            PendingEdit edit = pendingEdits.get(previewId);
            if (edit == null) throw new IOException("La propuesta ya no existe. Vuelve a generarla.");
            File root = projectRoot(edit.projectId);
            File target = resolveProjectFile(root, edit.relativePath, true);
            String current = target.exists() ? readLimited(target) : "";
            if (target.exists() != edit.fileExisted || !sha256(current).equals(edit.oldHash)) {
                pendingEdits.remove(previewId);
                throw new IOException("El archivo cambió desde la vista previa. Revisa el archivo y genera otra propuesta.");
            }

            File projectBackupDir = new File(backupsRoot, edit.projectId);
            if (!projectBackupDir.exists() && !projectBackupDir.mkdirs()) {
                throw new IOException("No se pudo crear la carpeta de copias de seguridad.");
            }
            JSONArray journal = readJournal(edit.projectId);
            JSONObject entry = new JSONObject();
            String backupName = UUID.randomUUID().toString() + ".bak";
            if (edit.fileExisted) {
                File backup = new File(projectBackupDir, backupName);
                writeAtomic(backup, current);
            }
            entry.put("path", edit.relativePath);
            entry.put("backup", edit.fileExisted ? backupName : "");
            entry.put("existed", edit.fileExisted);
            entry.put("createdAt", System.currentTimeMillis());
            journal.put(entry);
            trimJournal(projectBackupDir, journal);
            writeJournal(edit.projectId, journal);

            writeAtomic(target, edit.newContent);
            pendingEdits.remove(previewId);
            result.put("ok", true);
            result.put("path", edit.relativePath);
            result.put("summary", edit.summary);
            result.put("backupCreated", edit.fileExisted);
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    public synchronized JSONObject cancelEdit(String previewId) {
        JSONObject result = new JSONObject();
        pendingEdits.remove(previewId);
        try {
            result.put("ok", true);
            result.put("cancelled", true);
        } catch (JSONException ignored) { }
        return result;
    }

    /** Restores the last applied change, or deletes the last newly-created file. */
    public synchronized JSONObject undoLast(String projectId) {
        JSONObject result = new JSONObject();
        try {
            File root = projectRoot(projectId);
            JSONArray journal = readJournal(projectId);
            if (journal.length() == 0) throw new IOException("No hay cambios que deshacer.");
            JSONObject entry = journal.getJSONObject(journal.length() - 1);
            String relativePath = entry.getString("path");
            File target = resolveProjectFile(root, relativePath, true);
            if (entry.optBoolean("existed")) {
                File backup = new File(new File(backupsRoot, projectId), entry.getString("backup"));
                if (!backup.isFile()) throw new IOException("No se encontró la copia de seguridad del último cambio.");
                writeAtomic(target, readLimited(backup));
                backup.delete();
            } else {
                if (target.exists() && !target.delete()) throw new IOException("No se pudo eliminar el archivo nuevo.");
            }
            journal.remove(journal.length() - 1);
            writeJournal(projectId, journal);
            result.put("ok", true);
            result.put("path", relativePath);
            result.put("restored", entry.optBoolean("existed"));
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    /** Compact, bounded source context; only text files inside the selected project are read. */
    public synchronized String buildContext(String projectId) {
        try {
            File root = projectRoot(projectId);
            JSONObject listing = listFiles(projectId);
            JSONArray files = listing.optJSONArray("files");
            if (files == null) return "";
            List<String> paths = new ArrayList<>();
            for (int i = 0; i < files.length(); i++) paths.add(files.optString(i));
            List<String> preferred = Arrays.asList(
                    "project.godot", "main.tscn", "scripts/main.gd", "scripts/player.gd");
            paths.sort(Comparator.comparingInt(path -> {
                int rank = preferred.indexOf(path);
                return rank < 0 ? 100 : rank;
            }));

            StringBuilder context = new StringBuilder();
            context.append("Archivos del proyecto seleccionado (rutas relativas):\n");
            for (String path : paths) context.append("- ").append(path).append("\n");
            context.append("\nContenido de los archivos relevantes:\n");
            for (String path : paths) {
                if (context.length() >= MAX_CONTEXT_CHARS) break;
                File file = resolveProjectFile(root, path, false);
                String text = readLimited(file);
                int remaining = MAX_CONTEXT_CHARS - context.length();
                if (text.length() > remaining) text = text.substring(0, Math.max(0, remaining));
                if (text.isEmpty()) continue;
                context.append("\n--- ").append(path).append(" ---\n").append(text).append("\n");
            }
            return context.toString();
        } catch (Exception error) {
            return "No se pudo leer el proyecto: " + error.getMessage();
        }
    }

    private File projectRoot(String projectId) throws IOException {
        if (projectId == null || !projectId.matches("[a-z0-9-]{1,64}")) {
            throw new IOException("Selecciona un proyecto válido.");
        }
        File root = new File(projectsRoot, projectId).getCanonicalFile();
        File parent = projectsRoot.getCanonicalFile();
        if (!root.getPath().startsWith(parent.getPath() + File.separator)
                || !root.isDirectory()
                || !new File(root, "project.godot").isFile()) {
            throw new IOException("El proyecto seleccionado no existe o no es válido.");
        }
        return root;
    }

    private File resolveProjectFile(File root, String inputPath, boolean allowMissing) throws IOException {
        String relative = normalizePath(inputPath);
        if (relative.startsWith("/") || relative.contains(":")) throw new IOException("La ruta debe ser relativa.");
        String[] parts = relative.split("/");
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new IOException("La ruta contiene segmentos no permitidos.");
            }
        }
        if (!isTextExtension(relative)) throw new IOException("Este tipo de archivo no se puede editar desde el agente.");
        File target = new File(root, relative).getCanonicalFile();
        String base = root.getCanonicalPath() + File.separator;
        if (!target.getPath().startsWith(base)) throw new IOException("La ruta intenta salir de la carpeta del proyecto.");
        if (!allowMissing && !target.isFile()) throw new IOException("No se encontró el archivo solicitado.");
        return target;
    }

    private String normalizePath(String path) throws IOException {
        if (path == null) throw new IOException("Falta la ruta del archivo.");
        String normalized = path.trim().replace('\\', '/');
        if (normalized.isEmpty() || normalized.length() > 240 || normalized.indexOf('\0') >= 0) {
            throw new IOException("La ruta del archivo no es válida.");
        }
        return normalized;
    }

    private boolean isTextExtension(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".gd") || lower.endsWith(".tscn") || lower.endsWith(".tres")
                || lower.endsWith(".gdshader") || lower.endsWith(".shader")
                || lower.endsWith(".godot") || lower.endsWith(".json")
                || lower.endsWith(".cfg") || lower.endsWith(".md") || lower.endsWith(".txt");
    }

    private void collectFiles(File root, File directory, JSONArray output, int depth) throws JSONException {
        if (depth > 5 || output.length() >= MAX_LISTED_FILES) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            if (child.getName().startsWith(".") || ".godot".equals(child.getName())) continue;
            if (child.isDirectory()) {
                collectFiles(root, child, output, depth + 1);
            } else if (child.isFile() && isTextExtension(child.getName())) {
                output.put(root.toURI().relativize(child.toURI()).getPath());
            }
        }
    }

    private String readLimited(File file) throws IOException {
        if (file.length() > MAX_FILE_BYTES) throw new IOException("El archivo supera el límite de lectura de 128 KB.");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, count);
                if (buffer.size() > MAX_FILE_BYTES) throw new IOException("El archivo supera el límite de lectura de 128 KB.");
            }
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }

    private void writeAtomic(File target, String content) throws IOException {
        File parent = target.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) throw new IOException("No se pudo preparar la carpeta del archivo.");
        File temporary = new File(parent, target.getName() + ".gameforge-" + UUID.randomUUID() + ".tmp");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("El archivo supera el límite de escritura de 128 KB.");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.getFD().sync();
        }
        try {
            Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveUnavailable) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private File journalFile(String projectId) throws IOException {
        File directory = new File(backupsRoot, projectId);
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("No se pudo crear el historial de cambios.");
        return new File(directory, "history.json");
    }

    private JSONArray readJournal(String projectId) throws IOException {
        File file = journalFile(projectId);
        if (!file.isFile()) return new JSONArray();
        try {
            return new JSONArray(readLimited(file));
        } catch (JSONException error) {
            throw new IOException("El historial de copias de seguridad está dañado.", error);
        }
    }

    private void writeJournal(String projectId, JSONArray journal) throws IOException {
        writeAtomic(journalFile(projectId), journal.toString());
    }

    private void trimJournal(File backupDirectory, JSONArray journal) throws JSONException {
        while (journal.length() > MAX_BACKUPS) {
            JSONObject oldest = journal.getJSONObject(0);
            String backup = oldest.optString("backup", "");
            if (!backup.isEmpty()) new File(backupDirectory, backup).delete();
            journal.remove(0);
        }
    }

    private void putError(JSONObject result, Exception error) {
        try {
            result.put("ok", false);
            result.put("error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        } catch (JSONException ignored) { }
    }
}
