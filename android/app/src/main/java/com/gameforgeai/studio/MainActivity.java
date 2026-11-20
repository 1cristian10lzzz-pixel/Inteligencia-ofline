package com.gameforgeai.studio;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.graphics.Insets;
import android.view.WindowInsetsController;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ActivityManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 4101;
    private static final int REQUEST_IMPORT_MODEL = 4102;
    private static final String PROJECTS_DIR = "projects";
    private String pendingExportId;
    private static final String TAG = "GameForgeAI";
    private WebView webView;
    private FrameLayout rootLayout;
    private int renderRecoveryCount = 0;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService modelExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService generationExecutor = Executors.newSingleThreadExecutor();
    private ProjectWorkspace projectWorkspace;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectWorkspace = new ProjectWorkspace(getFilesDir());
        setStatusBar();
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.rgb(10, 14, 24));
        setContentView(rootLayout);
        openWebViewSafely();
    }

    /** Keeps the native Activity alive even if WebView cannot be created. */
    private void openWebViewSafely() {
        try {
            WebView view = new WebView(this);
            webView = view;
            view.setBackgroundColor(Color.rgb(10, 14, 24));
            view.getSettings().setJavaScriptEnabled(true);
            view.getSettings().setDomStorageEnabled(true);
            view.getSettings().setAllowFileAccess(true);
            view.getSettings().setAllowContentAccess(false);
            view.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
            view.getSettings().setSupportMultipleWindows(false);
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
            view.addJavascriptInterface(new GameForgeBridge(), "GameForge");
            view.setOnApplyWindowInsetsListener((target, insets) -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                    target.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                }
                return insets;
            });
            view.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage message) {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.e(TAG, "Web UI: " + message.message() + " (línea " + message.lineNumber() + ")");
                    }
                    return true;
                }
            });
            view.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView target, WebResourceRequest request) {
                    String scheme = request.getUrl().getScheme();
                    return !"file".equalsIgnoreCase(scheme) && !"about".equalsIgnoreCase(scheme);
                }

                @Override
                public void onPageFinished(WebView target, String url) {
                    super.onPageFinished(target, url);
                    if (url != null && url.startsWith("file:///android_asset/index.html")) {
                        target.evaluateJavascript(
                                "document.readyState === 'complete' && !!document.getElementById('messages')",
                                value -> {
                                    if ("true".equals(value)) {
                                        Log.i(TAG, "UI_READY");
                                    } else {
                                        Log.e(TAG, "UI_NOT_READY: documento local sin panel principal");
                                    }
                                });
                    }
                }

                @Override
                public void onReceivedError(WebView target, WebResourceRequest request, WebResourceError error) {
                    super.onReceivedError(target, request, error);
                    if (request != null && request.isForMainFrame()) {
                        String description = error == null ? "Error de carga desconocido" : String.valueOf(error.getDescription());
                        Log.e(TAG, "No se pudo cargar la interfaz local: " + description);
                        showStartupError("No se pudo abrir la interfaz de GameForge AI", description);
                    }
                }

                @Override
                public boolean onRenderProcessGone(WebView target, RenderProcessGoneDetail detail) {
                    Log.e(TAG, "El proceso de WebView terminó. Recuperable="
                            + (detail != null && detail.didCrash()));
                    if (rootLayout != null) rootLayout.removeView(target);
                    target.destroy();
                    if (webView == target) webView = null;

                    if (rootLayout != null && renderRecoveryCount == 0) {
                        renderRecoveryCount++;
                        rootLayout.postDelayed(() -> openWebViewSafely(), 350);
                    } else {
                        showStartupError(
                                "La interfaz se cerró inesperadamente",
                                "GameForge AI intentó recuperar WebView, pero no pudo. Cierra la aplicación y vuelve a abrirla.");
                    }
                    return true;
                }
            });
            rootLayout.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            view.loadUrl("file:///android_asset/index.html");
        } catch (RuntimeException | LinkageError failure) {
            Log.e(TAG, "Error iniciando la interfaz Android", failure);
            showStartupError("GameForge AI no pudo iniciar su interfaz", failure.getMessage());
        }
    }

    private void showStartupError(String title, String details) {
        runOnUiThread(() -> {
            TextView panel = new TextView(this);
            panel.setText(title + "\n\n" + (details == null ? "Sin detalles adicionales." : details)
                    + "\n\nLa aplicación no necesita Internet para abrir. Guarda este mensaje y compártelo para diagnosticar el teléfono.");
            panel.setTextColor(Color.rgb(237, 243, 255));
            panel.setTextSize(15);
            panel.setGravity(Gravity.CENTER);
            panel.setPadding(28, 28, 28, 28);
            panel.setBackgroundColor(Color.rgb(10, 14, 24));
            if (webView != null) {
                webView.stopLoading();
                webView.destroy();
                webView = null;
            }
            if (rootLayout == null) {
                setContentView(panel);
            } else {
                rootLayout.removeAllViews();
                rootLayout.addView(panel, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
        });
    }

    private void setStatusBar() {
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(10, 14, 24));
        window.setNavigationBarColor(Color.rgb(10, 14, 24));
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        generationExecutor.shutdownNow();
        modelExecutor.shutdownNow();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT && resultCode != RESULT_OK) {
            pendingExportId = null;
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT && pendingExportId != null) {
            final String projectId = pendingExportId;
            pendingExportId = null;
            toast("Exportando el proyecto…");
            ioExecutor.execute(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("No se pudo abrir el destino seleccionado.");
                    zipProject(projectId, out);
                    toast("Proyecto exportado correctamente.");
                } catch (Exception e) {
                    toast("Error al exportar: " + e.getMessage());
                }
            });
        } else if (requestCode == REQUEST_IMPORT_MODEL) {
            importModel(uri);
        }
    }

    private void importModel(Uri uri) {
        toast("Importando modelo en segundo plano…");
        ioExecutor.execute(() -> {
            File destination = null;
            try {
                String displayName = queryDisplayName(uri);
                if (displayName == null || !displayName.toLowerCase(Locale.ROOT).endsWith(".gguf")) {
                    throw new IOException("Selecciona un archivo de modelo .gguf.");
                }
                File models = new File(getFilesDir(), "models");
                if (!models.exists() && !models.mkdirs()) throw new IOException("No se pudo crear la biblioteca de modelos.");
                String safeName = sanitizeFileName(displayName);
                destination = new File(models, safeName);
                int suffix = 2;
                while (destination.exists()) {
                    int dot = safeName.lastIndexOf('.');
                    String stem = dot > 0 ? safeName.substring(0, dot) : safeName;
                    String extension = dot > 0 ? safeName.substring(dot) : ".gguf";
                    destination = new File(models, stem + "-" + suffix++ + extension);
                }
                InputStream raw = getContentResolver().openInputStream(uri);
                if (raw == null) throw new IOException("No se pudo leer el modelo.");
                try (InputStream in = new BufferedInputStream(raw);
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
                    byte[] buffer = new byte[1024 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                toast("Modelo GGUF guardado. Selecciónalo y pulsa Cargar modelo.");
                refreshWebView();
            } catch (Exception e) {
                if (destination != null && destination.exists()) destination.delete();
                toast("No se pudo importar el modelo: " + e.getMessage());
            }
        });
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void refreshWebView() {
        if (webView != null) webView.post(() -> webView.evaluateJavascript("window.refreshAll && window.refreshAll()", null));
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private File projectsRoot() {
        File root = new File(getFilesDir(), PROJECTS_DIR);
        if (!root.exists()) root.mkdirs();
        return root;
    }

    private void copyAssetTree(String assetPath, File destination) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            try (InputStream in = getAssets().open(assetPath); OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            return;
        }
        if (!destination.exists() && !destination.mkdirs()) throw new IOException("No se pudo crear " + destination);
        for (String child : children) {
            copyAssetTree(assetPath + "/" + child, new File(destination, child));
        }
    }

    private String makeSlug(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (normalized.isEmpty()) normalized = "mi-juego";
        return normalized.substring(0, Math.min(normalized.length(), 36));
    }

    private JSONObject createProject(String name, String type, String prompt) {
        JSONObject result = new JSONObject();
        try {
            String cleanName = name == null || name.trim().isEmpty() ? "Mi juego" : name.trim();
            String template = "platformer_2d".equals(type) ? "platformer_2d" : "survival_3d";
            String slugBase = makeSlug(cleanName);
            String slug = slugBase;
            int suffix = 2;
            while (new File(projectsRoot(), slug).exists()) slug = slugBase + "-" + suffix++;
            File projectDir = new File(projectsRoot(), slug);
            if (!projectDir.mkdirs()) throw new IOException("No se pudo crear la carpeta del proyecto.");
            copyAssetTree("templates/" + template, projectDir);

            File godotFile = new File(projectDir, "project.godot");
            String projectConfig = readText(godotFile);
            projectConfig = projectConfig.replace("GameForge Survival 3D", cleanName)
                    .replace("GameForge Platformer 2D", cleanName);
            writeText(godotFile, projectConfig);

            JSONObject metadata = new JSONObject();
            metadata.put("id", slug);
            metadata.put("name", cleanName);
            metadata.put("template", template);
            metadata.put("createdAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
            metadata.put("description", prompt == null ? "" : prompt);
            writeText(new File(projectDir, "gameforge-project.json"), metadata.toString(2));

            result.put("ok", true);
            result.put("id", slug);
            result.put("name", cleanName);
            result.put("template", template);
            result.put("path", projectDir.getAbsolutePath());
        } catch (Exception e) {
            try { result.put("ok", false); result.put("error", e.getMessage()); } catch (JSONException ignored) { }
        }
        return result;
    }

    private String readText(File file) throws IOException {
        byte[] bytes;
        try (InputStream in = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = in.read(chunk)) != -1) buffer.write(chunk, 0, count);
            bytes = buffer.toByteArray();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeText(File file, String content) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private JSONArray listProjects() {
        JSONArray array = new JSONArray();
        File[] folders = projectsRoot().listFiles(File::isDirectory);
        if (folders == null) return array;
        for (File folder : folders) {
            File metadata = new File(folder, "gameforge-project.json");
            if (!metadata.exists()) continue;
            try { array.put(new JSONObject(readText(metadata))); } catch (Exception ignored) { }
        }
        return array;
    }

    private JSONArray listModels() {
        JSONArray array = new JSONArray();
        File modelsDir = new File(getFilesDir(), "models");
        File[] files = modelsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".gguf"));
        if (files == null) return array;
        for (File file : files) {
            JSONObject item = new JSONObject();
            try {
                item.put("name", file.getName());
                item.put("sizeBytes", file.length());
                item.put("sizeMb", Math.max(1, file.length() / (1024 * 1024)));
                array.put(item);
            } catch (JSONException ignored) { }
        }
        return array;
    }

    private void loadModelAsync(String fileName, String requestId) {
        if (fileName == null || !fileName.matches("[A-Za-z0-9._-]{1,150}\\.gguf")) {
            notifyModelLoaded(requestId, false, "Nombre de archivo de modelo no válido.");
            return;
        }
        modelExecutor.execute(() -> {
            File model = new File(new File(getFilesDir(), "models"), fileName);
            if (!model.isFile()) {
                notifyModelLoaded(requestId, false, "No se encontró el modelo seleccionado.");
                return;
            }
            ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            if (activityManager != null) activityManager.getMemoryInfo(memoryInfo);
            if (memoryInfo.availMem > 0 && model.length() > (long) (memoryInfo.availMem * 1.5d)) {
                notifyModelLoaded(requestId, false, "El modelo parece demasiado grande para la memoria RAM libre. Prueba una cuantización más pequeña.");
                return;
            }
            boolean loaded = LocalLlmRuntime.loadModel(model.getAbsolutePath(), 2048, 4);
            String message = loaded ? "Modelo cargado. La inferencia funciona localmente." : LocalLlmRuntime.lastError();
            notifyModelLoaded(requestId, loaded, message);
        });
    }

    private void notifyModelLoaded(String requestId, boolean success, String message) {
        String id = JSONObject.quote(requestId == null ? "" : requestId);
        String payload = JSONObject.quote(message == null ? "" : message);
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(
                    "window.onGameForgeModelLoaded && window.onGameForgeModelLoaded(" + id + "," + success + "," + payload + ")", null);
            refreshWebView();
        });
    }

    private void askAsync(String prompt, String historyJson, String projectId, String requestId) {
        generationExecutor.execute(() -> {
            JSONObject response;
            if (LocalLlmRuntime.isModelLoaded()) {
                response = new JSONObject();
                try {
                    String modelPrompt = buildAgentPrompt(prompt, historyJson, projectId);
                    String answer = LocalLlmRuntime.generate(modelPrompt, 768, 0.7f);
                    response.put("answer", answer);
                    response.put("mode", "llama_cpp");
                    response.put("agentProjectId", projectId == null ? "" : projectId);
                    response.put("error", answer.startsWith("Error") || answer.startsWith("No hay un modelo"));
                    attachEditProposal(response, answer);
                } catch (Exception error) {
                    try {
                        response.put("answer", "No pude preparar la tarea del agente: " + error.getMessage());
                        response.put("mode", "llama_cpp");
                        response.put("error", true);
                    } catch (JSONException ignored) { }
                }
            } else {
                response = askLocalRules(prompt);
                try { response.put("agentProjectId", projectId == null ? "" : projectId); }
                catch (JSONException ignored) { }
            }
            String id = JSONObject.quote(requestId == null ? "" : requestId);
            String payload = JSONObject.quote(response.toString());
            runOnUiThread(() -> {
                if (webView != null) webView.evaluateJavascript(
                        "window.onGameForgeAnswer && window.onGameForgeAnswer(" + id + "," + payload + ")", null);
            });
        });
    }

    private String buildAgentPrompt(String prompt, String historyJson, String projectId) {
        StringBuilder message = new StringBuilder();
        message.append("Eres GameForge AI, un agente programador offline especializado en Godot y GDScript. ");
        message.append("Responde en español, sé preciso y no inventes archivos ni funciones. ");
        message.append("El usuario siempre revisa y aprueba cualquier edición antes de guardarla. ");
        message.append("Nunca afirmes que modificaste un archivo si no has emitido una propuesta.\n\n");

        if (projectId != null && projectId.matches("[a-z0-9-]{1,64}")) {
            message.append("PROYECTO ACTIVO: ").append(projectId).append("\n");
            message.append(projectWorkspace.buildContext(projectId)).append("\n");
            message.append("Solo propone ediciones a rutas relativas que aparezcan en el proyecto o un archivo .gd nuevo dentro de scripts/. ");
            message.append("Entrega el archivo completo, no un fragmento, y no superes 128 KB.\n");
        } else {
            message.append("No hay proyecto seleccionado. Puedes explicar, planificar o responder, pero no propongas cambios de archivos.\n");
        }

        try {
            JSONArray history = new JSONArray(historyJson == null ? "[]" : historyJson);
            int first = Math.max(0, history.length() - 4);
            if (history.length() > 0) message.append("\nCONVERSACIÓN RECIENTE:\n");
            for (int i = first; i < history.length(); i++) {
                JSONObject item = history.optJSONObject(i);
                if (item == null) continue;
                String role = "user".equals(item.optString("role")) ? "Usuario" : "Asistente";
                String content = item.optString("content", "");
                if (content.length() > 350) content = content.substring(0, 350);
                message.append(role).append(": ").append(content).append("\n");
            }
        } catch (JSONException ignored) {
            message.append("\nNo se pudo recuperar el historial anterior.\n");
        }

        message.append("\nPETICIÓN ACTUAL:\n").append(prompt == null ? "" : prompt).append("\n\n");
        message.append("Si hace falta cambiar un archivo y tienes suficiente contexto, termina con exactamente un bloque estructurado de este formato. ");
        message.append("El contenido debe ser JSON válido, con saltos de línea escapados dentro de content:\n");
        message.append("<gameforge-edit>{\"action\":\"edit_file\",\"path\":\"scripts/player.gd\",\"summary\":\"Explica brevemente el cambio\",\"content\":\"CONTENIDO COMPLETO DEL ARCHIVO\"}</gameforge-edit>\n");
        message.append("No uses ese bloque para sugerencias hipotéticas, no generes más de un archivo por respuesta y nunca escribas contraseñas, claves ni datos personales en el proyecto.");
        return message.toString();
    }

    private void attachEditProposal(JSONObject response, String answer) {
        if (answer == null) return;
        String open = "<gameforge-edit>";
        String close = "</gameforge-edit>";
        int start = answer.indexOf(open);
        int end = start < 0 ? -1 : answer.indexOf(close, start + open.length());
        if (start < 0 || end < 0) return;
        String json = answer.substring(start + open.length(), end).trim();
        try {
            JSONObject proposal = new JSONObject(json);
            if (!"edit_file".equals(proposal.optString("action"))
                    || proposal.optString("path").trim().isEmpty()
                    || !proposal.has("content")) return;
            response.put("editProposal", proposal);
            response.put("answer", (answer.substring(0, start) + answer.substring(end + close.length())).trim());
        } catch (JSONException invalidProposal) {
            try {
                response.put("answer", (answer.substring(0, start) + answer.substring(end + close.length())).trim()
                        + "\n\nLa propuesta de archivo llegó mal formada; no se ha cambiado ningún archivo.");
            } catch (JSONException ignored) { }
        }
    }

    private JSONObject runtimeStatus() {
        JSONObject info = new JSONObject();
        try {
            File modelsDir = new File(getFilesDir(), "models");
            File[] models = modelsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".gguf"));
            info.put("modelCount", models == null ? 0 : models.length);
            info.put("runtime", "llama_cpp_integrated");
            info.put("runtimeLabel", LocalLlmRuntime.status());
            info.put("runtimeVersion", LocalLlmRuntime.version());
            info.put("modelLoaded", LocalLlmRuntime.isModelLoaded());
            info.put("inferenceReady", LocalLlmRuntime.isModelLoaded());
            info.put("internet", false);
            info.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "desconocida");
            info.put("android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            if (activityManager != null) activityManager.getMemoryInfo(memoryInfo);
            info.put("ramMb", (int) (memoryInfo.totalMem / (1024 * 1024)));
            info.put("freeStorageMb", (int) (getFilesDir().getUsableSpace() / (1024 * 1024)));
            info.put("projectAgentTools", projectWorkspace != null);
        } catch (JSONException ignored) { }
        return info;
    }

    private JSONObject askLocalRules(String prompt) {
        JSONObject response = new JSONObject();
        String clean = prompt == null ? "" : prompt.trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        try {
            if (clean.isEmpty()) {
                response.put("answer", "Escribe qué videojuego quieres crear. Puedo preparar una plantilla 2D o 3D que podrás abrir en Godot.");
                response.put("mode", "rules");
                return response;
            }
            boolean create = lower.contains("crea") || lower.contains("crear") || lower.contains("genera") || lower.contains("construye") || lower.startsWith("haz un juego") || lower.startsWith("hazme un juego");
            if (create) {
                boolean twoD = lower.contains("2d") || lower.contains("plataforma") || lower.contains("platformer");
                String template = twoD ? "platformer_2d" : "survival_3d";
                String name = twoD ? "Nuevo juego 2D" : "Nuevo mundo 3D";
                JSONObject project = createProject(name, template, clean);
                response.put("project", project);
                response.put("mode", "local_template_agent");
                response.put("answer", project.optBoolean("ok")
                        ? "He preparado un proyecto Godot base a partir de tu petición. Es una plantilla funcional inicial, no una generación neuronal libre. Abre el proyecto en Godot para probarlo y ampliarlo."
                        : "No pude crear el proyecto: " + project.optString("error"));
                return response;
            }
            response.put("mode", "rules");
            response.put("answer", "Puedo ayudarte con un flujo local basado en plantillas. Para iniciar un juego escribe, por ejemplo: «Crea un juego 2D de plataformas» o «Crea un juego 3D de supervivencia». Importa y carga un modelo GGUF compatible para activar la inferencia neuronal local con llama.cpp.");
        } catch (JSONException e) {
            try { response.put("answer", "Error preparando respuesta local."); } catch (JSONException ignored) { }
        }
        return response;
    }

    private void startExport(String projectId) {
        if (projectId == null || !projectId.matches("[a-z0-9-]{1,64}")) {
            toast("Identificador de proyecto no válido.");
            return;
        }
        File project = new File(projectsRoot(), projectId);
        if (!project.isDirectory() || !new File(project, "project.godot").isFile()) {
            toast("No se encontró el proyecto.");
            return;
        }
        pendingExportId = projectId;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, projectId + "-godot-source.zip");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void zipProject(String projectId, OutputStream output) throws IOException {
        File root = new File(projectsRoot(), projectId);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            zipDirectory(root, root, zip);
            zip.finish();
        }
    }

    private void zipDirectory(File root, File current, ZipOutputStream zip) throws IOException {
        File[] files = current.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                zipDirectory(root, file, zip);
            } else {
                String path = root.toURI().relativize(file.toURI()).getPath();
                zip.putNextEntry(new ZipEntry(path));
                try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) zip.write(buffer, 0, read);
                }
                zip.closeEntry();
            }
        }
    }

    public class GameForgeBridge {
        @JavascriptInterface public String getProjects() { return listProjects().toString(); }
        @JavascriptInterface public String getModels() { return listModels().toString(); }
        @JavascriptInterface public String getRuntimeStatus() { return runtimeStatus().toString(); }
        @JavascriptInterface public String createProject(String name, String type, String prompt) { return MainActivity.this.createProject(name, type, prompt).toString(); }
        @JavascriptInterface public void askAsync(String prompt, String historyJson, String projectId, String requestId) {
            MainActivity.this.askAsync(prompt, historyJson, projectId, requestId);
        }
        @JavascriptInterface public void loadModel(String fileName, String requestId) { loadModelAsync(fileName, requestId); }
        @JavascriptInterface public void unloadModel() { modelExecutor.execute(LocalLlmRuntime::unloadModel); }
        @JavascriptInterface public String listProjectFiles(String projectId) { return projectWorkspace.listFiles(projectId).toString(); }
        @JavascriptInterface public String readProjectFile(String projectId, String path) { return projectWorkspace.readFile(projectId, path).toString(); }
        @JavascriptInterface public String previewProjectEdit(String projectId, String proposalJson) { return projectWorkspace.previewEdit(projectId, proposalJson).toString(); }
        @JavascriptInterface public String applyProjectEdit(String previewId) { return projectWorkspace.applyEdit(previewId).toString(); }
        @JavascriptInterface public String cancelProjectEdit(String previewId) { return projectWorkspace.cancelEdit(previewId).toString(); }
        @JavascriptInterface public String undoLastProjectEdit(String projectId) { return projectWorkspace.undoLast(projectId).toString(); }
        @JavascriptInterface public void exportProject(String id) { runOnUiThread(() -> startExport(id)); }
        @JavascriptInterface public void importModel() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/x-gguf", "*/*"});
                startActivityForResult(intent, REQUEST_IMPORT_MODEL);
            });
        }
        @JavascriptInterface public String getStorageInfo() {
            JSONObject result = new JSONObject();
            try {
                result.put("freeBytes", getFilesDir().getUsableSpace());
                result.put("privatePath", getFilesDir().getAbsolutePath());
            } catch (JSONException ignored) { }
            return result.toString();
        }
    }
}
