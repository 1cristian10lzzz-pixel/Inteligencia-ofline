package com.gameforgeai.studio;

/** llama.cpp runtime. GGUF files are imported by the user and processed entirely on-device. */
public final class LocalLlmRuntime {
    private static final boolean LIBRARY_LOADED;
    private static final String LOAD_ERROR;

    static {
        boolean loaded = false;
        String error = "";
        try {
            System.loadLibrary("gameforge_llama");
            loaded = true;
        } catch (UnsatisfiedLinkError | SecurityException failure) {
            error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        }
        LIBRARY_LOADED = loaded;
        LOAD_ERROR = error;
    }

    private LocalLlmRuntime() { }

    private static native String nativeVersion();
    private static native boolean nativeLoadModel(String path, int contextSize, int threads);
    private static native boolean nativeIsModelLoaded();
    private static native String nativeLastError();
    private static native String nativeGenerate(String prompt, int maxTokens, float temperature);
    private static native void nativeUnloadModel();

    public static boolean isLibraryLoaded() { return LIBRARY_LOADED; }
    public static boolean isModelLoaded() {
        if (!LIBRARY_LOADED) return false;
        try { return nativeIsModelLoaded(); } catch (UnsatisfiedLinkError error) { return false; }
    }
    public static String version() {
        if (!LIBRARY_LOADED) return "";
        try { return nativeVersion(); } catch (UnsatisfiedLinkError error) { return ""; }
    }
    public static String status() {
        if (!LIBRARY_LOADED) return "Runtime llama.cpp no disponible: " + LOAD_ERROR;
        if (isModelLoaded()) return "llama.cpp " + version() + " · modelo GGUF cargado en el dispositivo";
        return "llama.cpp integrado · falta cargar un modelo GGUF";
    }
    public static boolean loadModel(String path, int contextSize, int threads) {
        if (!LIBRARY_LOADED) return false;
        try { return nativeLoadModel(path, contextSize, threads); }
        catch (UnsatisfiedLinkError error) { return false; }
    }
    public static String lastError() {
        if (!LIBRARY_LOADED) return LOAD_ERROR;
        try { return nativeLastError(); } catch (UnsatisfiedLinkError error) { return error.getMessage(); }
    }
    public static String generate(String prompt, int maxTokens, float temperature) {
        if (!isModelLoaded()) return "No hay un modelo GGUF cargado.";
        try { return nativeGenerate(prompt, maxTokens, temperature); }
        catch (UnsatisfiedLinkError error) { return "Error de inferencia nativa: " + error.getMessage(); }
    }
    public static void unloadModel() {
        if (!LIBRARY_LOADED) return;
        try { nativeUnloadModel(); } catch (UnsatisfiedLinkError ignored) { }
    }
}
