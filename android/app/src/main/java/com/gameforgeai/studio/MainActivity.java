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
import java.util.concurrent.atomic.AtomicBoolean;
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
    private MinefadeProjectWorkspace minefadeWorkspace;
    private volatile boolean bundledBootstrapRunning = false;
    private volatile boolean minefadeProjectReady = false;
    private volatile String bootstrapStatus = "Preparando Minefade y la IA local…";
    private final AtomicBoolean generationRunning = new AtomicBoolean(false);
    private final AtomicBoolean generationCancelRequested = new AtomicBoolean(false);
    private volatile int activeContextSize = 0;
    private volatile int activeThreadCount = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectWorkspace = new ProjectWorkspace(getFilesDir());
        minefadeWorkspace = new MinefadeProjectWorkspace(getFilesDir());
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.rgb(10, 14, 24));
        setContentView(rootLayout);
        // DecorView/WindowInsetsController are not guaranteed to exist before content is attached.
        setStatusBar();
        openWebViewSafely();
        bootstrapBundledAssetsAndModel();
    }

    /** Keeps the native Activity alive even if WebView cannot be created. */
    private void openWebViewSafely() {
        try {
            WebView view = new WebView(this);
            webView = view;
            // Prevent Android from treating the visible WebView renderer as an empty process.
            view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
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
                    if (url != null && url.startsWith("file:///android_asset/studio.html")) {
                        target.evaluateJavascript(
                                "document.readyState === 'complete' && !!document.getElementById('appRoot')",
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
            view.loadUrl("file:///android_asset/studio.html");
        } catch (RuntimeException | LinkageError failure) {
            Log.e(TAG, "Error iniciando la interfaz Android", failure);
            showStartupError("GameForge AI no pudo iniciar su interfaz", failure.getMessage());
        }
    }

    private void bootstrapBundledAssetsAndModel() {
        bundledBootstrapRunning = true;
        bootstrapStatus = "Instalando Minefade y el modelo local incluido…";
        modelExecutor.execute(() -> {
            boolean success = false;
            String message;
            try {
                File minefade = BuiltinAssetsInstaller.ensureMinefadeProject(this, getFilesDir());
                minefadeProjectReady = minefade.isDirectory();
                Log.i(TAG, "MINEFADE_PROJECT_READY " + minefade.getAbsolutePath());
                bootstrapStatus = "Copiando el modelo de IA incluido…";
                File model = BuiltinAssetsInstaller.ensureBundledModel(this, getFilesDir());
                ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                if (manager != null) manager.getMemoryInfo(memory);
                long totalRam = memory.totalMem;
                int contextSize = chooseContextSize(memory);
                int threads = chooseThreadCount();
                activeContextSize = contextSize;
                activeThreadCount = threads;
                bootstrapStatus = "Cargando Qwen2.5-Coder en el motor nativo…";
                Log.i(TAG, "MODEL_LOADING bytes=" + model.length() + " context=" + contextSize);
                boolean loaded = LocalLlmRuntime.loadModel(model.getAbsolutePath(), contextSize, threads);
                if (!loaded) throw new IOException("No se pudo cargar el modelo incluido: " + LocalLlmRuntime.lastError());
                Log.i(TAG, "MODEL_READY");
                bootstrapStatus = "IA local lista · Minefade instalado";
                if (BuildConfig.DEBUG) {
                    StringBuilder longSelfTestPrompt = new StringBuilder("Comprueba el contexto largo de Minefade y responde OK. ");
                    for (int i = 0; i < 40; i++) {
                        longSelfTestPrompt.append("Minefade usa C++20, Rust, JNI, OpenGL ES 3, RmlUi, chunks, bloques, shaders, memoria y mundos MFVOX. ");
                    }
                    String selfTest = LocalLlmRuntime.generate(longSelfTestPrompt.toString(), 24, 0.1f);
                    if (selfTest == null || selfTest.trim().isEmpty()
                            || selfTest.startsWith("Error") || selfTest.startsWith("No hay un modelo")) {
                        throw new IOException("La prueba de inferencia del modelo no pasó: " + selfTest);
                    }
                    Log.i(TAG, "INFERENCE_SELF_TEST_PASS chars=" + selfTest.length());
                }
                success = true;
                message = bootstrapStatus;
            } catch (Exception error) {
                message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                bootstrapStatus = "No se pudo preparar la IA local: " + message;
                Log.e(TAG, "BUNDLE_BOOTSTRAP_FAILED " + message, error);
            } finally {
                bundledBootstrapRunning = false;
            }
            final boolean ready = success;
            final String finalMessage = message;
            runOnUiThread(() -> {
                if (webView != null) {
                    webView.evaluateJavascript("window.onGameForgeBootstrap && window.onGameForgeBootstrap("
                            + ready + "," + JSONObject.quote(finalMessage) + ");"
                            + "window.refreshAll && window.refreshAll();", null);
                }
            });
        });
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
        final Window window = getWindow();
        if (window == null) return;

        try {
            window.setStatusBarColor(Color.rgb(10, 14, 24));
            window.setNavigationBarColor(Color.rgb(10, 14, 24));

            if (Build.VERSION.SDK_INT >= 30) {
                // Run after setContentView: on API 30+, getInsetsController() can dereference
                // a DecorView that has not been attached yet and crash Activity.onCreate().
                final android.view.View decor = window.getDecorView();
                if (decor != null) {
                    decor.post(() -> {
                        try {
                            WindowInsetsController controller = window.getInsetsController();
                            if (controller != null) {
                                controller.setSystemBarsAppearance(
                                        0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                            }
                        } catch (RuntimeException error) {
                            Log.w(TAG, "No se pudo ajustar el aspecto de las barras del sistema", error);
                        }
                    });
                }
            }
        } catch (RuntimeException error) {
            // A cosmetic system-bar issue must never prevent the application from opening.
            Log.w(TAG, "Se omitió la personalización de las barras del sistema", error);
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
            long totalRam = memoryInfo.totalMem;
            int contextSize = chooseContextSize(memoryInfo);
            int threadCount = chooseThreadCount();
            activeContextSize = contextSize;
            activeThreadCount = threadCount;
            boolean loaded = LocalLlmRuntime.loadModel(model.getAbsolutePath(), contextSize, threadCount);
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
        if (prompt == null || prompt.trim().isEmpty()) return;
        if (!generationRunning.compareAndSet(false, true)) {
            notifyAgentAnswer(requestId, new JSONObject(), "Ya hay una generación en curso. Deténla antes de iniciar otra.");
            return;
        }
        generationCancelRequested.set(false);
        generationExecutor.execute(() -> {
            JSONObject response = new JSONObject();
            try {
                if (LocalLlmRuntime.isModelLoaded()) {
                    notifyGenerationStarted(requestId);
                    String modelPrompt = buildAgentPrompt(prompt, historyJson, projectId);
                    String answer;
                    boolean cancelled;
                    if (generationCancelRequested.get()) {
                        answer = "";
                        cancelled = true;
                    } else {
                        answer = LocalLlmRuntime.generate(modelPrompt, 768, 0.7f);
                        cancelled = generationCancelRequested.get() || LocalLlmRuntime.wasLastGenerationCancelled();
                    }
                    if (cancelled) {
                        response.put("answer", answer == null || answer.isEmpty()
                                ? "Generación detenida." : answer + "\n\n[Generación detenida]");
                    } else {
                        response.put("answer", answer == null ? "" : answer);
                    }
                    response.put("mode", "llama_cpp");
                    response.put("agentProjectId", projectId == null ? "" : projectId);
                    response.put("cancelled", cancelled);
                    response.put("error", !cancelled && isGenerationError(answer));
                    if (!cancelled && !isGenerationError(answer)) attachEditProposal(response, answer);
                } else if ("minefade".equals(projectId)) {
                    response.put("answer", "La IA de Minefade todavía está preparando el modelo incluido. Espera a que el estado indique «IA local lista» y vuelve a enviar tu petición. No se ha creado ni modificado ningún proyecto.");
                    response.put("mode", "bootstrap");
                    response.put("error", true);
                    response.put("agentProjectId", "minefade");
                } else {
                    JSONObject local = askLocalRules(prompt);
                    response = local;
                    response.put("agentProjectId", projectId == null ? "" : projectId);
                }
            } catch (Exception error) {
                try {
                    response.put("answer", "No pude completar la tarea del agente: "
                            + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                    response.put("mode", "llama_cpp");
                    response.put("error", true);
                } catch (JSONException ignored) { }
            } finally {
                generationRunning.set(false);
                generationCancelRequested.set(false);
            }
            notifyAgentAnswer(requestId, response, null);
        });
    }

    private boolean isGenerationError(String answer) {
        if (answer == null || answer.trim().isEmpty()) return true;
        return answer.startsWith("Error") || answer.startsWith("No hay un modelo")
                || answer.startsWith("El texto es demasiado largo")
                || answer.startsWith("Falló") || answer.startsWith("No se pudo")
                || answer.startsWith("No hay memoria");
    }

    private void notifyGenerationStarted(String requestId) {
        String id = JSONObject.quote(requestId == null ? "" : requestId);
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(
                    "window.onGameForgeGenerationStarted && window.onGameForgeGenerationStarted(" + id + ")", null);
        });
    }

    private void notifyAgentAnswer(String requestId, JSONObject response, String forcedError) {
        if (forcedError != null) {
            try { response.put("answer", forcedError); response.put("error", true); }
            catch (JSONException ignored) { }
        }
        String id = JSONObject.quote(requestId == null ? "" : requestId);
        String payload = JSONObject.quote(response.toString());
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(
                    "window.onGameForgeAnswer && window.onGameForgeAnswer(" + id + "," + payload + ")", null);
        });
    }

    private void cancelCurrentGeneration() {
        if (!generationRunning.get()) return;
        generationCancelRequested.set(true);
        LocalLlmRuntime.cancelGeneration();
    }

    private int chooseContextSize(ActivityManager.MemoryInfo memory) {
        if (memory == null || memory.lowMemory) return 1536;
        if (memory.availMem >= 1_500_000_000L) return 3072;
        if (memory.availMem >= 900_000_000L) return 2048;
        return 1536;
    }

    private int chooseThreadCount() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(2, Math.min(4, processors - 1));
    }

    private String buildAgentPrompt(String prompt, String historyJson, String projectId) {
        StringBuilder message = new StringBuilder();
        boolean minefade = "minefade".equals(projectId) && minefadeWorkspace != null && minefadeWorkspace.isReady();
        if (minefade) {
            message.append("Eres GameForge AI, agente de ingeniería especializado exclusivamente en Minefade 2.0. ");
            message.append("Responde en español. Minefade es un motor nativo Android, no un proyecto Godot: C++20, Rust ABI C, JNI, OpenGL ES 3, RmlUi 6.3, FreeType y Gradle/NDK. ");
            message.append("Inspecciona el mapa y los fragmentos reales antes de proponer código. No inventes rutas, funciones, APIs ni resultados de pruebas. ");
            message.append("Respeta los formatos MFVOX001, MFVOX002 y MFVOX003 y los IDs de bloque; nunca los cambies sin migración versionada. ");
            message.append("No reemplaces subsistemas enteros cuando baste una corrección localizada. No afirmes haber compilado, ejecutado o probado si no existe un resultado real.\n\n");
            int contextBudget = Math.max(1800, Math.min(6500, activeContextSize * 2));
            message.append(minefadeWorkspace.buildContext(prompt, contextBudget));
            message.append("\n\nCONTRATO DE CAMBIOS MULTIARCHIVO:\n");
            message.append("Si se necesita cambiar código, termina con exactamente un bloque <minefade-change-set>JSON</minefade-change-set>. ");
            message.append("JSON: action=edit_files, summary y files (1 a 12 elementos). ");
            message.append("Para archivos existentes se prefieren patches: [{find,replace,occurrence?}]. find debe ser un fragmento literal exacto y corto; occurrence solo si hay varias coincidencias. No reescribas archivos enteros cuando baste un parche. ");
            message.append("Usa content solo para archivos nuevos o cuando un parche exacto no sea razonable. Cada content debe ser el archivo completo. ");
            message.append("Respeta rutas relativas reales, no edites secretos ni configuración de firma y modifica solo lo necesario. No incluyas Markdown dentro del JSON. ");
            message.append("Si falta contexto o no puedes señalar fragmentos exactos, solicita leer los archivos y no generes una propuesta.\n");
        } else {
            message.append("Eres GameForge AI, un asistente experto en desarrollo de videojuegos Android. ");
            message.append("Responde en español, da pasos concretos y no afirmes haber modificado archivos si no lo has hecho.\n");
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
                if (content.length() > 320) content = content.substring(0, 320);
                message.append(role).append(": ").append(content).append("\n");
            }
        } catch (JSONException ignored) {
            message.append("\nNo se pudo recuperar el historial anterior.\n");
        }
        message.append("\nPETICIÓN ACTUAL:\n").append(prompt == null ? "" : prompt);
        return message.toString();
    }

    private void attachEditProposal(JSONObject response, String answer) {
        if (answer == null) return;
        String batchOpen = "<minefade-change-set>";
        String batchClose = "</minefade-change-set>";
        int batchStart = answer.indexOf(batchOpen);
        int batchEnd = batchStart < 0 ? -1 : answer.indexOf(batchClose, batchStart + batchOpen.length());
        if (batchStart >= 0 && batchEnd > batchStart) {
            String json = answer.substring(batchStart + batchOpen.length(), batchEnd).trim();
            try {
                JSONObject proposal = new JSONObject(json);
                JSONArray files = proposal.optJSONArray("files");
                if ("edit_files".equals(proposal.optString("action")) && files != null
                        && files.length() >= 1 && files.length() <= 8) {
                    response.put("batchProposal", proposal);
                    response.put("answer", (answer.substring(0, batchStart)
                            + answer.substring(batchEnd + batchClose.length())).trim());
                    return;
                }
            } catch (JSONException ignored) { }
            try {
                response.put("answer", (answer.substring(0, batchStart)
                        + answer.substring(batchEnd + batchClose.length())).trim()
                        + "\n\nLa propuesta multiarchivo llegó mal formada; no se ha cambiado ningún archivo.");
                response.put("error", true);
            } catch (JSONException ignored) { }
            return;
        }
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
                response.put("error", true);
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
            info.put("projectAgentTools", projectWorkspace != null && minefadeWorkspace != null);
            info.put("bootstrapRunning", bundledBootstrapRunning);
            info.put("bootstrapStatus", bootstrapStatus);
            info.put("minefadeProjectReady", minefadeProjectReady || (minefadeWorkspace != null && minefadeWorkspace.isReady()));
            info.put("minefadeSourceFiles", minefadeWorkspace == null ? 0 : minefadeWorkspace.getStatus().optInt("sourceFiles", 0));
            info.put("builtinModel", true);
            info.put("modelName", "Qwen2.5-Coder-0.5B-Instruct Q3_K_M");
            info.put("activeContextSize", activeContextSize);
            info.put("activeThreadCount", activeThreadCount);
            info.put("availableRamMb", (int) (memoryInfo.availMem / (1024 * 1024)));
            info.put("lowMemoryDevice", memoryInfo.lowMemory);
            info.put("generationRunning", generationRunning.get());
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
        if (!project.isDirectory() || (!new File(project, "project.godot").isFile()
                && !new File(project, "gameforge-project.json").isFile())) {
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
        @JavascriptInterface public void cancelGeneration() { cancelCurrentGeneration(); }
        @JavascriptInterface public String getProjectChangeHistory(String projectId) {
            return "minefade".equals(projectId) ? minefadeWorkspace.getChangeHistory().toString()
                    : "{\"ok\":true,\"tasks\":[],\"count\":0}";
        }
        @JavascriptInterface public String createProject(String name, String type, String prompt) { return MainActivity.this.createProject(name, type, prompt).toString(); }
        @JavascriptInterface public void askAsync(String prompt, String historyJson, String projectId, String requestId) {
            MainActivity.this.askAsync(prompt, historyJson, projectId, requestId);
        }
        @JavascriptInterface public void loadModel(String fileName, String requestId) { loadModelAsync(fileName, requestId); }
        @JavascriptInterface public void unloadModel() { modelExecutor.execute(LocalLlmRuntime::unloadModel); }
        @JavascriptInterface public String listProjectFiles(String projectId) {
            return "minefade".equals(projectId) ? minefadeWorkspace.listFiles().toString() : projectWorkspace.listFiles(projectId).toString();
        }
        @JavascriptInterface public String readProjectFile(String projectId, String path) {
            return "minefade".equals(projectId) ? minefadeWorkspace.readFile(path).toString() : projectWorkspace.readFile(projectId, path).toString();
        }
        @JavascriptInterface public String previewProjectEdit(String projectId, String proposalJson) {
            return "minefade".equals(projectId) ? minefadeWorkspace.previewBatch(proposalJson).toString() : projectWorkspace.previewEdit(projectId, proposalJson).toString();
        }
        @JavascriptInterface public String applyProjectEdit(String previewId) { return projectWorkspace.applyEdit(previewId).toString(); }
        @JavascriptInterface public String cancelProjectEdit(String previewId) { return projectWorkspace.cancelEdit(previewId).toString(); }
        @JavascriptInterface public String undoLastProjectEdit(String projectId) {
            return "minefade".equals(projectId) ? minefadeWorkspace.undoLast().toString() : projectWorkspace.undoLast(projectId).toString();
        }
        @JavascriptInterface public String searchProjectFiles(String query, int limit) { return minefadeWorkspace.searchFiles(query, limit).toString(); }
        @JavascriptInterface public String previewProjectBatchEdit(String projectId, String proposalJson) {
            if (!"minefade".equals(projectId)) return "{\"ok\":false,\"error\":\"Las propuestas multiarchivo están limitadas al proyecto Minefade.\"}";
            return minefadeWorkspace.previewBatch(proposalJson).toString();
        }
        @JavascriptInterface public String applyProjectBatchEdit(String previewId) { return minefadeWorkspace.applyBatch(previewId).toString(); }
        @JavascriptInterface public String cancelProjectBatchEdit(String previewId) { return minefadeWorkspace.cancelBatch(previewId).toString(); }
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
