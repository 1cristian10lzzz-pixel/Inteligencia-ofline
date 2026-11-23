package com.gameforgeai.studio;

/** llama.cpp runtime. The native library is loaded only when the user starts model loading. */
public final class LocalLlmRuntime {
    private static volatile boolean libraryLoaded = false;
    private static volatile String loadError = "El motor nativo todavía no se ha inicializado.";
    private static boolean loadAttempted = false;

    private LocalLlmRuntime() { }

    private static native String nativeVersion();
    private static native boolean nativeLoadModel(String path, int contextSize, int threads);
    private static native boolean nativeIsModelLoaded();
    private static native String nativeLastError();
    private static native String nativeGenerate(String prompt, int maxTokens, float temperature);
    private static native void nativeCancelGeneration();
    private static native boolean nativeWasGenerationCancelled();
    private static native void nativeUnloadModel();

    /** Initializes llama.cpp once on the caller's worker thread, never in class startup. */
    private static synchronized boolean ensureLibraryLoaded() {
        if (libraryLoaded) return true;
        if (loadAttempted) return false;

        loadAttempted = true;
        try {
            System.loadLibrary("gameforge_llama");
            libraryLoaded = true;
            loadError = "";
        } catch (LinkageError | SecurityException failure) {
            loadError = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            libraryLoaded = false;
        }
        return libraryLoaded;
    }

    public static boolean isLibraryLoaded() { return libraryLoaded; }

    public static boolean isModelLoaded() {
        if (!libraryLoaded) return false;
        try { return nativeIsModelLoaded(); }
        catch (LinkageError | RuntimeException error) { return false; }
    }

    public static String version() {
        if (!libraryLoaded) return "";
        try { return nativeVersion(); }
        catch (LinkageError | RuntimeException error) { return ""; }
    }

    public static String status() {
        if (!libraryLoaded) {
            return loadAttempted
                    ? "Runtime llama.cpp no disponible: " + loadError
                    : "llama.cpp integrado · se inicializa al cargar un modelo GGUF";
        }
        if (isModelLoaded()) return "llama.cpp " + version() + " · modelo GGUF cargado en el dispositivo";
        return "llama.cpp integrado · falta cargar un modelo GGUF";
    }

    public static boolean loadModel(String path, int contextSize, int threads) {
        if (!ensureLibraryLoaded()) return false;
        try {
            boolean loaded = nativeLoadModel(path, contextSize, threads);
            if (!loaded) {
                String nativeError = nativeLastError();
                if (nativeError != null && !nativeError.trim().isEmpty()) loadError = nativeError;
            }
            return loaded;
        } catch (LinkageError | RuntimeException error) {
            loadError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            return false;
        }
    }

    public static String lastError() {
        if (!libraryLoaded) return loadError;
        try {
            String nativeError = nativeLastError();
            return nativeError == null || nativeError.trim().isEmpty() ? loadError : nativeError;
        } catch (LinkageError | RuntimeException error) {
            return error.getMessage() == null ? loadError : error.getMessage();
        }
    }

    public static String generate(String prompt, int maxTokens, float temperature) {
        if (!isModelLoaded()) return "No hay un modelo GGUF cargado.";
        try { return nativeGenerate(prompt, maxTokens, temperature); }
        catch (LinkageError | RuntimeException error) { return "Error de inferencia nativa: " + error.getMessage(); }
    }

    public static void cancelGeneration() {
        if (!libraryLoaded) return;
        try { nativeCancelGeneration(); }
        catch (LinkageError | RuntimeException ignored) { }
    }

    public static boolean wasLastGenerationCancelled() {
        if (!libraryLoaded) return false;
        try { return nativeWasGenerationCancelled(); }
        catch (LinkageError | RuntimeException error) { return false; }
    }

    public static void unloadModel() {
        if (!libraryLoaded) return;
        try { nativeUnloadModel(); }
        catch (LinkageError | RuntimeException ignored) {
            // The activity can still close cleanly if the native backend has already failed.
        }
    }
}
