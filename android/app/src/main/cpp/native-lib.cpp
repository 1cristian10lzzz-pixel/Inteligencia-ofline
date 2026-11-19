#include <jni.h>
#include <android/log.h>
#include <llama.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <stdexcept>
#include <mutex>
#include <string>
#include <vector>
#include <unistd.h>

#define LOG_TAG "GameForgeLLM"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex g_mutex;
llama_model *g_model = nullptr;
llama_context *g_context = nullptr;
llama_sampler *g_sampler = nullptr;
std::string g_last_error;
bool g_backend_initialized = false;

void set_error(const std::string &message) { g_last_error = message; LOGE("%s", message.c_str()); }
void release_model_locked() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}
std::string from_jstring(JNIEnv *env, jstring value) {
    if (!value) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text) {
    std::vector<llama_token> tokens(std::max<size_t>(32, text.size() + 8));
    int32_t count = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()),
                                   tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (count < 0) {
        tokens.resize(static_cast<size_t>(-count));
        count = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()),
                               tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (count < 0) throw std::runtime_error("No se pudo tokenizar el texto del modelo.");
    tokens.resize(static_cast<size_t>(count));
    return tokens;
}
std::string make_prompt(const std::string &user_text) {
    const char *system_text =
        "Eres GameForge AI, un asistente experto en crear videojuegos para Android. "
        "Ayuda con Godot 4, GDScript, C++, Kotlin, diseño de juegos, escenas, recursos y depuración. "
        "Responde en español, da pasos concretos y no afirmes haber modificado archivos si no lo has hecho.";
    llama_chat_message messages[2] = {{"system", system_text}, {"user", user_text.c_str()}};
    const char *chat_template = llama_model_chat_template(g_model, nullptr);
    if (chat_template) {
        std::vector<char> buffer(std::max<size_t>(2048, (std::strlen(system_text) + user_text.size()) * 4 + 256));
        int32_t size = llama_chat_apply_template(chat_template, messages, 2, true, buffer.data(), static_cast<int32_t>(buffer.size()));
        if (size >= 0) {
            if (static_cast<size_t>(size) > buffer.size()) {
                buffer.resize(static_cast<size_t>(size) + 1);
                size = llama_chat_apply_template(chat_template, messages, 2, true, buffer.data(), static_cast<int32_t>(buffer.size()));
            }
            if (size >= 0) return std::string(buffer.data(), static_cast<size_t>(size));
        }
    }
    return std::string("System: ") + system_text + "\nUser: " + user_text + "\nAssistant:";
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_gameforgeai_studio_LocalLlmRuntime_nativeVersion(JNIEnv *env, jclass) {
    return env->NewStringUTF(llama_version());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gameforgeai_studio_LocalLlmRuntime_nativeLoadModel(JNIEnv *env, jclass, jstring model_path,
                                                             jint context_size, jint threads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_model_locked();
    g_last_error.clear();
    const std::string path = from_jstring(env, model_path);
    if (path.empty()) { set_error("Ruta de modelo vacía."); return JNI_FALSE; }

    if (!g_backend_initialized) { llama_backend_init(); g_backend_initialized = true; }
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // Portable CPU baseline for Android devices.
    g_model = llama_model_load_from_file(path.c_str(), model_params);
    if (!g_model) { set_error("llama.cpp no pudo abrir el modelo GGUF. Comprueba que el archivo esté completo y sea compatible."); return JNI_FALSE; }

    llama_context_params context_params = llama_context_default_params();
    const int safe_context = std::clamp(static_cast<int>(context_size), 512, 4096);
    context_params.n_ctx = static_cast<uint32_t>(safe_context);
    context_params.n_batch = 256;
    context_params.n_ubatch = 128;
    const long cpu_count = sysconf(_SC_NPROCESSORS_ONLN);
    const int default_threads = static_cast<int>(std::clamp(cpu_count > 2 ? cpu_count - 2 : 2L, 2L, 4L));
    const int safe_threads = std::clamp(static_cast<int>(threads), 1, 6);
    context_params.n_threads = safe_threads > 0 ? safe_threads : default_threads;
    context_params.n_threads_batch = context_params.n_threads;
    g_context = llama_init_from_model(g_model, context_params);
    if (!g_context) { release_model_locked(); set_error("No hay memoria suficiente para crear el contexto del modelo."); return JNI_FALSE; }

    auto sampler_params = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sampler_params);
    if (!g_sampler) { release_model_locked(); set_error("No se pudo crear el muestreador del modelo."); return JNI_FALSE; }
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.70f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gameforgeai_studio_LocalLlmRuntime_nativeIsModelLoaded(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_model && g_context && g_sampler ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_gameforgeai_studio_LocalLlmRuntime_nativeLastError(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return env->NewStringUTF(g_last_error.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_gameforgeai_studio_LocalLlmRuntime_nativeGenerate(JNIEnv *env, jclass, jstring prompt_value,
                                                            jint max_tokens, jfloat temperature) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context || !g_sampler) {
        set_error("Primero debes cargar un modelo GGUF.");
        return env->NewStringUTF(g_last_error.c_str());
    }
    try {
        const std::string user_text = from_jstring(env, prompt_value);
        const std::string formatted = make_prompt(user_text);
        const llama_vocab *vocab = llama_model_get_vocab(g_model);
        const std::vector<llama_token> prompt_tokens = tokenize(vocab, formatted);
        const uint32_t context_limit = llama_n_ctx(g_context);
        if (prompt_tokens.empty() || prompt_tokens.size() + 8 >= context_limit) {
            set_error("El texto es demasiado largo para el contexto actual. Reduce el mensaje.");
            return env->NewStringUTF(g_last_error.c_str());
        }

        llama_memory_clear(llama_get_memory(g_context), false);
        const float temp = std::clamp(static_cast<float>(temperature), 0.05f, 1.2f);
        llama_sampler_free(g_sampler);
        auto sampler_params = llama_sampler_chain_default_params();
        g_sampler = llama_sampler_chain_init(sampler_params);
        llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.90f, 1));
        llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(temp));
        llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        llama_batch batch = llama_batch_init(static_cast<int32_t>(prompt_tokens.size()), 0, 1);
        for (size_t i = 0; i < prompt_tokens.size(); ++i) {
            batch.token[i] = prompt_tokens[i];
            batch.pos[i] = static_cast<llama_pos>(i);
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = (i + 1 == prompt_tokens.size()) ? 1 : 0;
        }
        const int32_t prompt_result = llama_decode(g_context, batch);
        llama_batch_free(batch);
        if (prompt_result != 0) { set_error("Falló el procesamiento inicial del modelo."); return env->NewStringUTF(g_last_error.c_str()); }

        const int limit = std::clamp(static_cast<int>(max_tokens), 16, 768);
        std::string output;
        llama_pos position = static_cast<llama_pos>(prompt_tokens.size());
        for (int i = 0; i < limit && position < static_cast<llama_pos>(context_limit - 1); ++i) {
            const llama_token token = llama_sampler_sample(g_sampler, g_context, -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            llama_sampler_accept(g_sampler, token);
            char piece[1024];
            const int32_t piece_size = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
            if (piece_size > 0) output.append(piece, static_cast<size_t>(piece_size));
            else if (piece_size < 0) { set_error("La pieza generada excede el buffer del token."); break; }

            llama_token next = token;
            llama_batch next_batch = llama_batch_init(1, 0, 1);
            next_batch.token[0] = next;
            next_batch.pos[0] = position;
            next_batch.n_seq_id[0] = 1;
            next_batch.seq_id[0][0] = 0;
            next_batch.logits[0] = 1;
            const int32_t decode_result = llama_decode(g_context, next_batch);
            llama_batch_free(next_batch);
            if (decode_result != 0) { set_error("Falló la generación del siguiente token."); break; }
            ++position;
        }
        if (output.empty() && !g_last_error.empty()) return env->NewStringUTF(g_last_error.c_str());
        g_last_error.clear();
        return env->NewStringUTF(output.c_str());
    } catch (const std::exception &error) {
        set_error(error.what());
        return env->NewStringUTF(g_last_error.c_str());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gameforgeai_studio_LocalLlmRuntime_nativeUnloadModel(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_model_locked();
}
