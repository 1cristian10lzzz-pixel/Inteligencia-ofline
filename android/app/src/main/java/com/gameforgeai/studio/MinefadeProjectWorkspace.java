package com.gameforgeai.studio;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Restricted, Minefade-specific source workspace.
 * Supports bounded inspection, batch previews, hash checks, atomic file writes,
 * transaction backups, and undo without granting shell or filesystem-wide access.
 */
public final class MinefadeProjectWorkspace {
    private static final int MAX_FILE_BYTES = 512 * 1024;
    private static final int MAX_FILES = 1800;
    private static final int MAX_DEPTH = 9;
    private static final int MAX_BATCH_FILES = 12;
    private static final int MAX_PATCHES_PER_FILE = 24;
    private static final int MAX_TRANSACTIONS = 12;
    private static final int MAX_CONTEXT_CHARS = 7600;

    private final File root;
    private final File backupsRoot;
    private final Map<String, PendingBatch> pending = new HashMap<>();

    private static final class PendingFile {
        final String path;
        final String oldHash;
        final String newContent;
        final boolean existed;
        PendingFile(String path, String oldHash, String newContent, boolean existed) {
            this.path = path;
            this.oldHash = oldHash;
            this.newContent = newContent;
            this.existed = existed;
        }
    }

    private static final class PendingBatch {
        final String summary;
        final List<PendingFile> files;
        PendingBatch(String summary, List<PendingFile> files) {
            this.summary = summary;
            this.files = files;
        }
    }

    public MinefadeProjectWorkspace(File filesDir) {
        this.root = new File(new File(filesDir, "projects"), "minefade");
        this.backupsRoot = new File(new File(filesDir, "project-backups"), "minefade");
    }

    public synchronized boolean isReady() {
        return root.isDirectory() && new File(root, "gameforge-project.json").isFile()
                && new File(root, "app/src/main/cpp").isDirectory();
    }

    public synchronized JSONObject listFiles() {
        JSONObject result = new JSONObject();
        try {
            requireReady();
            JSONArray files = new JSONArray();
            collectFiles(root, root, files, 0);
            result.put("ok", true);
            result.put("files", files);
            result.put("count", files.length());
            result.put("truncated", files.length() >= MAX_FILES);
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    public synchronized JSONObject readFile(String relativePath) {
        JSONObject result = new JSONObject();
        try {
            requireReady();
            File file = resolve(relativePath, false);
            result.put("ok", true);
            result.put("path", normalize(relativePath));
            result.put("content", readLimited(file));
            result.put("bytes", file.length());
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    public synchronized JSONObject searchFiles(String rawQuery, int limit) {
        JSONObject result = new JSONObject();
        try {
            requireReady();
            String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
            if (query.length() < 2) throw new IOException("Escribe al menos dos caracteres para buscar.");
            int max = Math.max(1, Math.min(100, limit));
            JSONArray matches = new JSONArray();
            JSONArray files = new JSONArray();
            collectFiles(root, root, files, 0);
            for (int i = 0; i < files.length() && matches.length() < max; i++) {
                String path = files.getString(i);
                File file = resolve(path, false);
                if (file.length() > MAX_FILE_BYTES) continue;
                String content = readLimited(file);
                String lower = content.toLowerCase(Locale.ROOT);
                int offset = lower.indexOf(query);
                if (offset < 0 && !path.toLowerCase(Locale.ROOT).contains(query)) continue;
                JSONObject hit = new JSONObject();
                hit.put("path", path);
                if (offset >= 0) {
                    int start = Math.max(0, offset - 120);
                    int end = Math.min(content.length(), offset + query.length() + 180);
                    hit.put("excerpt", content.substring(start, end).replace('\n', ' '));
                } else {
                    hit.put("excerpt", "Coincidencia en el nombre del archivo.");
                }
                matches.put(hit);
            }
            result.put("ok", true);
            result.put("matches", matches);
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    /** Builds a small, task-oriented project map for the included local model. */
    public synchronized String buildContext(String task) {
        return buildContext(task, MAX_CONTEXT_CHARS);
    }

    public synchronized String buildContext(String task, int requestedLimit) {
        try {
            requireReady();
            JSONArray listing = new JSONArray();
            collectFiles(root, root, listing, 0);
            List<String> paths = new ArrayList<>();
            for (int i = 0; i < listing.length(); i++) paths.add(listing.getString(i));
            Collections.sort(paths);

            StringBuilder out = new StringBuilder();
            out.append("PROYECTO: Minefade 2.0. Proyecto Android nativo; NO es Godot. ")
                    .append("Arquitectura: Android Java/JNI + C++20 + Rust ABI C + OpenGL ES 3 + RmlUi 6.3 + FreeType. ")
                    .append("No cambies el formato de mundo MFVOX001/002/003 ni IDs de bloque sin migración versionada.\n");
            out.append("ARCHIVOS (rutas relativas; solo los listados existen):\n");
            for (String path : paths) {
                if (out.length() > Math.min(2800, contextLimit / 2)) break;
                out.append("- ").append(path).append('\n');
            }

            int contextLimit = Math.max(1800, Math.min(MAX_CONTEXT_CHARS, requestedLimit));
            String lower = task == null ? "" : task.toLowerCase(Locale.ROOT);
            List<String> preferred = new ArrayList<>();
            preferred.add("README.md");
            preferred.add("docs/PROJECT_STATUS_2_0.md");
            preferred.add("docs/ENGINE_ARCHITECTURE.md");
            preferred.add("app/src/main/java/com/minefade/nativeengine/MainActivity.java");
            if (containsAny(lower, "render", "pantalla", "blanca", "franja", "shader", "agua", "sombra", "gpu", "chunk")) {
                preferred.addAll(Arrays.asList(
                        "docs/RENDERER_UPGRADE_PLAN_2_0.md",
                        "app/src/main/cpp/render/WorldMeshRenderer.cpp",
                        "app/src/main/cpp/render/WorldMeshRenderer.h",
                        "app/src/main/cpp/world/VoxelWorld.cpp",
                        "app/src/main/cpp/world/VoxelWorld.h",
                        "app/src/main/cpp/engine/Engine.cpp"));
            }
            if (containsAny(lower, "control", "jugador", "movimiento", "avatar", "tercera persona", "cámara", "camara")) {
                preferred.addAll(Arrays.asList(
                        "app/src/main/java/com/minefade/nativeengine/MinefadeSurfaceView.java",
                        "app/src/main/cpp/game/Player.cpp",
                        "app/src/main/cpp/game/Player.h",
                        "rust-core/src/lib.rs"));
            }
            if (containsAny(lower, "bloque", "mundo", "guardado", "inventario", "objeto", "minado")) {
                preferred.addAll(Arrays.asList(
                        "app/src/main/cpp/world/VoxelWorld.cpp",
                        "app/src/main/cpp/blocks/BlockRegistry.cpp",
                        "app/src/main/cpp/blocks/BlockRegistry.h",
                        "app/src/main/java/com/minefade/nativeengine/WorldManager.java"));
            }
            if (containsAny(lower, "entidad", "animal", "criatura", "enemigo", "ia", "npc")) {
                preferred.addAll(Arrays.asList(
                        "app/src/main/cpp/engine/Engine.cpp",
                        "rust-core/src/lib.rs",
                        "docs/MODDING_SYSTEM_AUDIT_AND_ROADMAP.md"));
            }
            if (containsAny(lower, "lan", "multijugador", "servidor", "red")) {
                preferred.add("app/src/main/java/com/minefade/nativeengine/LanMultiplayerService.java");
            }
            if (containsAny(lower, "mod", "textura", "receta", "bioma", "estructura", "bloque personalizado")) {
                preferred.add("docs/MODDING_SYSTEM_AUDIT_AND_ROADMAP.md");
                preferred.add("app/src/main/java/com/minefade/nativeengine/ModManager.java");
            }
            List<String> unique = new ArrayList<>();
            for (String path : preferred) if (!unique.contains(path) && paths.contains(path)) unique.add(path);
            out.append("\nCONTEXTO DE ARCHIVOS IMPORTANTES:\n");
            for (String path : unique) {
                if (out.length() >= contextLimit) break;
                File file = resolve(path, false);
                String content;
                try { content = readLimited(file); }
                catch (IOException large) { continue; }
                int remaining = contextLimit - out.length();
                int cap = Math.min(1100, Math.max(0, remaining - path.length() - 20));
                if (content.length() > cap) content = content.substring(0, cap) + "\n[CONTENIDO RECORTADO; pide inspección del archivo completo]\n";
                out.append("\n--- ").append(path).append(" ---\n").append(content).append('\n');
            }
            out.append("\nREGLAS: inspecciona los archivos existentes; no inventes clases ni APIs; ")
                    .append("mantén los cambios compatibles con Android y los formatos de guardado; ")
                    .append("no afirmes haber compilado o probado si no hay resultados de herramientas. ");
            return out.toString();
        } catch (Exception error) {
            return "No se pudo construir el contexto de Minefade: " + error.getMessage();
        }
    }

    /** Accepts a single structured change set and prepares a review-only preview. */
    public synchronized JSONObject previewBatch(String proposalJson) {
        JSONObject result = new JSONObject();
        try {
            requireReady();
            JSONObject proposal = new JSONObject(proposalJson == null ? "{}" : proposalJson);
            String action = proposal.optString("action", "edit_files");
            if (!"edit_files".equals(action)) throw new IOException("La acción propuesta no es válida.");
            JSONArray files = proposal.optJSONArray("files");
            if (files == null || files.length() < 1 || files.length() > MAX_BATCH_FILES) {
                throw new IOException("Una tarea debe modificar entre 1 y " + MAX_BATCH_FILES + " archivos.");
            }
            String summary = proposal.optString("summary", "Cambios propuestos para Minefade").trim();
            List<PendingFile> staged = new ArrayList<>();
            JSONArray preview = new JSONArray();
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < files.length(); i++) {
                JSONObject item = files.getJSONObject(i);
                String path = normalize(item.optString("path", ""));
                if (seen.contains(path)) throw new IOException("La propuesta repite el archivo " + path + ".");
                seen.add(path);
                File target = resolve(path, true);
                boolean exists = target.isFile();
                if (!exists && !isAllowedNewFileLocation(path)) {
                    throw new IOException("No se permite crear archivos en esa ubicación: " + path);
                }
                String before = exists ? readLimited(target) : "";
                String content;
                if (item.has("patches")) {
                    if (!exists) throw new IOException("No se pueden aplicar parches a un archivo nuevo: " + path);
                    content = applyExactPatches(before, item.getJSONArray("patches"), path);
                } else {
                    content = item.optString("content", "");
                }
                if (content.indexOf('\u0000') >= 0
                        || content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
                    throw new IOException("El archivo " + path + " supera 512 KB o contiene datos no válidos.");
                }
                PendingFile edit = new PendingFile(path, sha256(before), content, exists);
                staged.add(edit);

                JSONObject row = new JSONObject();
                row.put("path", path);
                row.put("summary", item.optString("summary", ""));
                row.put("before", previewText(before, 6000));
                row.put("after", previewText(content, 6000));
                row.put("created", !exists);
                row.put("validation", validateText(path, content));
                preview.put(row);
            }
            String previewId = UUID.randomUUID().toString();
            pending.put(previewId, new PendingBatch(summary, staged));
            result.put("ok", true);
            result.put("previewId", previewId);
            result.put("summary", summary);
            result.put("files", preview);
            result.put("fileCount", staged.size());
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    /** Validates all source hashes, writes the complete batch, and rolls back on any failure. */
    public synchronized JSONObject applyBatch(String previewId) {
        JSONObject result = new JSONObject();
        try {
            requireReady();
            PendingBatch batch = pending.get(previewId);
            if (batch == null) throw new IOException("La propuesta caducó. Vuelve a solicitarla.");
            File backupDir = backupsRoot;
            if (!backupDir.exists() && !backupDir.mkdirs()) throw new IOException("No se pudo crear el historial de copias.");
            List<String> backupNames = new ArrayList<>();
            for (PendingFile edit : batch.files) {
                File target = resolve(edit.path, true);
                boolean exists = target.isFile();
                String before = exists ? readLimited(target) : "";
                if (exists != edit.existed || !sha256(before).equals(edit.oldHash)) {
                    pending.remove(previewId);
                    throw new IOException("El archivo " + edit.path + " cambió desde la vista previa. Revisa y vuelve a generar los cambios.");
                }
                String backupName = "";
                if (exists) {
                    backupName = UUID.randomUUID() + ".bak";
                    writeAtomic(new File(backupDir, backupName), before);
                }
                backupNames.add(backupName);
            }

            int written = 0;
            try {
                for (PendingFile edit : batch.files) {
                    File target = resolve(edit.path, true);
                    writeAtomic(target, edit.newContent);
                    written++;
                }
                JSONArray history = readHistory();
                JSONObject transaction = new JSONObject();
                transaction.put("id", UUID.randomUUID().toString());
                transaction.put("summary", batch.summary);
                transaction.put("createdAt", System.currentTimeMillis());
                JSONArray changes = new JSONArray();
                for (int i = 0; i < batch.files.size(); i++) {
                    PendingFile edit = batch.files.get(i);
                    JSONObject change = new JSONObject();
                    change.put("path", edit.path);
                    change.put("backup", backupNames.get(i));
                    change.put("existed", edit.existed);
                    change.put("newHash", sha256(edit.newContent));
                    changes.put(change);
                }
                transaction.put("files", changes);
                history.put(transaction);
                trimHistory(history);
                writeHistory(history);
            } catch (Exception failure) {
                for (int i = written - 1; i >= 0; i--) {
                    PendingFile edit = batch.files.get(i);
                    File target = resolve(edit.path, true);
                    String backupName = backupNames.get(i);
                    if (edit.existed && !backupName.isEmpty()) {
                        writeAtomic(target, readLimited(new File(backupDir, backupName)));
                    } else if (target.exists()) {
                        target.delete();
                    }
                }
                throw failure;
            }

            pending.remove(previewId);
            JSONArray changed = new JSONArray();
            for (PendingFile edit : batch.files) changed.put(edit.path);
            result.put("ok", true);
            result.put("summary", batch.summary);
            result.put("files", changed);
            result.put("fileCount", batch.files.size());
            result.put("backupCreated", true);
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    public synchronized JSONObject cancelBatch(String previewId) {
        JSONObject result = new JSONObject();
        pending.remove(previewId);
        try {
            result.put("ok", true);
            result.put("cancelled", true);
        } catch (JSONException ignored) { }
        return result;
    }

    /** Restores the most recent complete change set, only if files still match that transaction. */
    public synchronized JSONObject undoLast() {
        JSONObject result = new JSONObject();
        try {
            requireReady();
            JSONArray history = readHistory();
            if (history.length() == 0) throw new IOException("No hay tareas aplicadas que se puedan deshacer.");
            JSONObject transaction = history.getJSONObject(history.length() - 1);
            JSONArray changes = transaction.getJSONArray("files");
            List<String> previousContents = new ArrayList<>();
            List<File> targets = new ArrayList<>();
            List<File> backups = new ArrayList<>();
            for (int i = 0; i < changes.length(); i++) {
                JSONObject change = changes.getJSONObject(i);
                File target = resolve(change.getString("path"), true);
                if (!target.isFile() || !sha256(readLimited(target)).equals(change.getString("newHash"))) {
                    throw new IOException("El archivo " + change.getString("path")
                            + " cambió después de la tarea. No se restauró nada para evitar perder trabajo.");
                }
                targets.add(target);
                previousContents.add(readLimited(target));
                if (change.optBoolean("existed")) {
                    File backup = new File(backupsRoot, change.getString("backup"));
                    if (!backup.isFile()) throw new IOException("Falta una copia de seguridad para " + change.getString("path") + ". No se restauró ningún archivo.");
                    backups.add(backup);
                } else {
                    backups.add(null);
                }
            }
            int restored = 0;
            try {
                for (int i = changes.length() - 1; i >= 0; i--) {
                    JSONObject change = changes.getJSONObject(i);
                    File target = targets.get(i);
                    if (change.optBoolean("existed")) {
                        writeAtomic(target, readLimited(backups.get(i)));
                    } else if (target.exists() && !target.delete()) {
                        throw new IOException("No se pudo retirar el archivo nuevo " + change.getString("path") + ".");
                    }
                    restored++;
                }
                history.remove(history.length() - 1);
                writeHistory(history);
            } catch (Exception restoreFailure) {
                for (int i = 0; i < changes.length(); i++) {
                    try {
                        if (targets.get(i).getParentFile() != null && !targets.get(i).getParentFile().exists()) {
                            targets.get(i).getParentFile().mkdirs();
                        }
                        writeAtomic(targets.get(i), previousContents.get(i));
                    } catch (Exception rollbackFailure) {
                        restoreFailure.addSuppressed(rollbackFailure);
                    }
                }
                throw restoreFailure;
            }
            for (File backup : backups) if (backup != null) backup.delete();
            result.put("ok", true);
            result.put("summary", transaction.optString("summary"));
            result.put("fileCount", changes.length());
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    /** Applies deterministic exact text patches; ambiguous matches are rejected. */
    private String applyExactPatches(String original, JSONArray patches, String path) throws Exception {
        if (patches.length() < 1 || patches.length() > MAX_PATCHES_PER_FILE) {
            throw new IOException("El archivo " + path + " debe tener entre 1 y " + MAX_PATCHES_PER_FILE + " parches.");
        }
        String result = original;
        for (int i = 0; i < patches.length(); i++) {
            JSONObject patch = patches.getJSONObject(i);
            String find = patch.optString("find", "");
            String replace = patch.optString("replace", "");
            if (find.isEmpty()) throw new IOException("El parche " + (i + 1) + " de " + path + " no contiene el texto que debe localizar.");
            if (find.length() > 16000 || replace.length() > 24000) {
                throw new IOException("El parche " + (i + 1) + " de " + path + " es demasiado grande.");
            }
            int count = countOccurrences(result, find);
            int occurrence = patch.optInt("occurrence", 0);
            if (count == 0) throw new IOException("No se encontró el fragmento del parche " + (i + 1) + " en " + path + ". No se aplicó ningún archivo.");
            if (occurrence <= 0 && count != 1) {
                throw new IOException("El fragmento del parche " + (i + 1) + " aparece " + count
                        + " veces en " + path + ". Añade occurrence para seleccionar una coincidencia exacta.");
            }
            if (occurrence > count) throw new IOException("La coincidencia " + occurrence + " no existe en " + path + ".");
            int position = -1;
            int from = 0;
            for (int hit = 1; hit <= occurrence || (occurrence == 0 && hit == 1); hit++) {
                position = result.indexOf(find, from);
                if (position < 0) break;
                from = position + find.length();
                if (occurrence == 0 || hit == occurrence) break;
            }
            if (position < 0) throw new IOException("No se pudo localizar la coincidencia seleccionada en " + path + ".");
            result = result.substring(0, position) + replace + result.substring(position + find.length());
            if (result.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
                throw new IOException("Los parches hacen que " + path + " supere 512 KB.");
            }
        }
        return result;
    }

    private int countOccurrences(String source, String needle) {
        int count = 0;
        int position = 0;
        while ((position = source.indexOf(needle, position)) >= 0) {
            count++;
            position += needle.length();
        }
        return count;
    }

    /** Returns the bounded, user-visible task history for the Tasks screen. */
    public synchronized JSONObject getChangeHistory() {
        JSONObject result = new JSONObject();
        try {
            JSONArray history = readHistory();
            JSONArray tasks = new JSONArray();
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject transaction = history.getJSONObject(i);
                JSONArray files = transaction.optJSONArray("files");
                JSONArray paths = new JSONArray();
                if (files != null) {
                    for (int j = 0; j < files.length(); j++) {
                        JSONObject change = files.getJSONObject(j);
                        paths.put(change.optString("path"));
                    }
                }
                JSONObject task = new JSONObject();
                task.put("id", transaction.optString("id"));
                task.put("summary", transaction.optString("summary"));
                task.put("createdAt", transaction.optLong("createdAt"));
                task.put("fileCount", paths.length());
                task.put("files", paths);
                task.put("canUndo", i == history.length() - 1);
                tasks.put(task);
            }
            result.put("ok", true);
            result.put("tasks", tasks);
            result.put("count", tasks.length());
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    public synchronized JSONObject getStatus() {
        JSONObject result = new JSONObject();
        try {
            JSONArray files = new JSONArray();
            if (isReady()) collectFiles(root, root, files, 0);
            result.put("ok", isReady());
            result.put("name", "Minefade");
            result.put("root", root.getAbsolutePath());
            result.put("sourceFiles", files.length());
            result.put("pendingReviews", pending.size());
            result.put("undoTransactions", readHistory().length());
        } catch (Exception error) {
            putError(result, error);
        }
        return result;
    }

    private void requireReady() throws IOException {
        if (!isReady()) throw new IOException("El código de Minefade no está instalado dentro de la aplicación.");
    }

    private void collectFiles(File base, File directory, JSONArray output, int depth) throws JSONException {
        if (depth > MAX_DEPTH || output.length() >= MAX_FILES) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith(".") || name.equals("build") || name.equals("target")
                    || name.equals("outputs") || name.equals(".gradle") || name.equals(".idea")
                    || name.equals("node_modules")) continue;
            if (child.isDirectory()) {
                collectFiles(base, child, output, depth + 1);
            } else if (child.isFile() && isTextFile(name) && child.length() <= MAX_FILE_BYTES) {
                output.put(base.toURI().relativize(child.toURI()).getPath());
            }
            if (output.length() >= MAX_FILES) return;
        }
    }

    private File resolve(String rawPath, boolean allowMissing) throws IOException {
        String path = normalize(rawPath);
        if (!isTextFile(path)) throw new IOException("El agente solo puede trabajar con archivos de texto permitidos.");
        File base = root.getCanonicalFile();
        File target = new File(base, path).getCanonicalFile();
        if (!target.getPath().startsWith(base.getPath() + File.separator)) {
            throw new IOException("La ruta intenta salir de Minefade.");
        }
        if (!allowMissing && !target.isFile()) throw new IOException("No existe el archivo " + path + ".");
        if (target.exists() && !target.isFile()) throw new IOException("La ruta seleccionada no es un archivo.");
        if (!allowMissing && target.length() > MAX_FILE_BYTES) {
            throw new IOException("El archivo supera el límite de lectura de 512 KB.");
        }
        return target;
    }

    private String normalize(String raw) throws IOException {
        if (raw == null) throw new IOException("Falta la ruta del archivo.");
        String path = raw.trim().replace('\\', '/');
        if (path.isEmpty() || path.length() > 240 || path.indexOf('\u0000') >= 0
                || path.startsWith("/") || path.contains(":")) {
            throw new IOException("La ruta no es válida.");
        }
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new IOException("La ruta contiene segmentos no permitidos.");
            }
        }
        if (isProtectedPath(path)) throw new IOException("Ese archivo contiene configuración sensible y está protegido.");
        return path;
    }

    private boolean isTextFile(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".c")
                || lower.endsWith(".h") || lower.endsWith(".hpp") || lower.endsWith(".rs")
                || lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".xml")
                || lower.endsWith(".kts") || lower.endsWith(".gradle") || lower.endsWith(".properties")
                || lower.endsWith(".toml") || lower.endsWith(".md") || lower.endsWith(".json")
                || lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".py")
                || lower.endsWith(".sh") || lower.endsWith(".glsl") || lower.endsWith(".vert")
                || lower.endsWith(".frag") || lower.endsWith(".cfg") || lower.endsWith(".txt")
                || lower.endsWith(".cmake") || lower.endsWith(".mfmod") || lower.endsWith(".mfshader");
    }

    private boolean isProtectedPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith("gameforge-project.json") || lower.endsWith("gameforge-bundle.json")
                || lower.startsWith(".github/workflows/") || lower.equals("gradle.properties")
                || lower.endsWith("local.properties") || lower.endsWith(".jks")
                || lower.endsWith(".keystore") || lower.endsWith(".env")
                || lower.contains("/secrets/") || lower.contains("credentials")
                || lower.contains("signingconfig") || lower.endsWith("keystore.properties");
    }

    private boolean isAllowedNewFileLocation(String path) {
        return path.startsWith("app/src/main/cpp/")
                || path.startsWith("app/src/main/java/")
                || path.startsWith("app/src/main/res/")
                || path.startsWith("rust-core/src/")
                || path.startsWith("rust-core/include/")
                || path.startsWith("tests/") || path.startsWith("tools/")
                || path.startsWith("docs/") || path.startsWith("examples/")
                || path.equals("README.md") || path.equals("CHANGELOG.md")
                || path.equals("Cargo.toml") || path.equals("build.gradle.kts")
                || path.equals("settings.gradle.kts") || path.equals("gradle.properties");
    }

    private String readLimited(File file) throws IOException {
        if (file.length() > MAX_FILE_BYTES) throw new IOException("El archivo supera el límite de 512 KB.");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, count);
                if (buffer.size() > MAX_FILE_BYTES) throw new IOException("El archivo supera el límite de 512 KB.");
            }
        }
        String value = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        if (value.indexOf('\u0000') >= 0) throw new IOException("El archivo no parece ser texto UTF-8.");
        return value;
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }

    private void writeAtomic(File target, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES || content.indexOf('\u0000') >= 0) {
            throw new IOException("El archivo propuesto supera el límite permitido.");
        }
        File parent = target.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) throw new IOException("No se pudo preparar la carpeta del archivo.");
        File temporary = new File(parent, target.getName() + ".gameforge-" + UUID.randomUUID() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
        try {
            Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException unavailable) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private JSONArray readHistory() throws IOException {
        File history = new File(backupsRoot, "history.json");
        if (!history.isFile()) return new JSONArray();
        try {
            if (history.length() > 256 * 1024) throw new IOException("El historial de cambios es demasiado grande.");
            return new JSONArray(readLimitedHistory(history));
        } catch (JSONException error) {
            throw new IOException("El historial de cambios está dañado.", error);
        }
    }

    private String readLimitedHistory(File file) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private void writeHistory(JSONArray history) throws IOException {
        if (!backupsRoot.exists() && !backupsRoot.mkdirs()) throw new IOException("No se pudo guardar el historial de cambios.");
        writeAtomic(new File(backupsRoot, "history.json"), history.toString());
    }

    private void trimHistory(JSONArray history) throws JSONException {
        while (history.length() > MAX_TRANSACTIONS) {
            JSONObject old = history.getJSONObject(0);
            JSONArray files = old.optJSONArray("files");
            if (files != null) {
                for (int i = 0; i < files.length(); i++) {
                    String backup = files.getJSONObject(i).optString("backup", "");
                    if (!backup.isEmpty()) new File(backupsRoot, backup).delete();
                }
            }
            history.remove(0);
        }
    }

    private String validateText(String path, String content) {
        String lower = path.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".json")) new JSONObject(content);
            if (lower.endsWith(".xml")) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(content)));
            }
            if (lower.endsWith(".cpp") || lower.endsWith(".h") || lower.endsWith(".hpp")
                    || lower.endsWith(".rs") || lower.endsWith(".java") || lower.endsWith(".kt")
                    || lower.endsWith(".gd") || lower.endsWith(".glsl") || lower.endsWith(".vert")
                    || lower.endsWith(".frag")) {
                String warning = delimiterWarning(content);
                if (warning != null) return warning;
            }
            return "Sin errores estructurales detectados; no sustituye al compilador real.";
        } catch (Exception error) {
            return "Error de formato: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    /** Lightweight delimiter check only; the real C++/Rust/Android compile remains authoritative. */
    private String delimiterWarning(String text) {
        ArrayList<Character> stack = new ArrayList<>();
        boolean lineComment = false, blockComment = false, escaped = false;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (lineComment) { if (c == '\n') lineComment = false; continue; }
            if (blockComment) { if (c == '*' && next == '/') { blockComment = false; i++; } continue; }
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '/' && next == '/') { lineComment = true; i++; continue; }
            if (c == '/' && next == '*') { blockComment = true; i++; continue; }
            if (c == '"' || c == '\'') { quote = c; continue; }
            if (c == '{' || c == '[' || c == '(') stack.add(c);
            else if (c == '}' || c == ']' || c == ')') {
                if (stack.isEmpty()) return "Delimitadores desbalanceados: cierre sin apertura.";
                char open = stack.remove(stack.size() - 1);
                if (!((open == '{' && c == '}') || (open == '[' && c == ']') || (open == '(' && c == ')'))) {
                    return "Delimitadores desbalanceados: se esperaba el cierre correspondiente.";
                }
            }
        }
        if (quote != 0 || blockComment || !stack.isEmpty()) return "Delimitadores incompletos al final del archivo.";
        return null;
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private String previewText(String content, int max) {
        if (content.length() <= max) return content;
        return content.substring(0, max) + "\n[Vista previa recortada]";
    }

    private void putError(JSONObject result, Exception error) {
        try {
            result.put("ok", false);
            result.put("error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        } catch (JSONException ignored) { }
    }
}
