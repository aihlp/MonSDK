#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"

namespace {
constexpr const char * TAG = "MonSDK-Llama";

void throw_java(JNIEnv * env, const char * message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

std::string token_piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(256);
    int size = llama_token_to_piece(vocab, token, buffer.data(), buffer.size(), 0, true);
    if (size < 0) {
        buffer.resize(-size);
        size = llama_token_to_piece(vocab, token, buffer.data(), buffer.size(), 0, true);
    }
    if (size < 0) throw std::runtime_error("Unable to decode generated token");
    return {buffer.data(), static_cast<size_t>(size)};
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medmonitoring_core_ai_AndroidLlamaRuntime_nativeGenerate(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jstring prompt,
    jstring grammar,
    jint max_tokens,
    jfloat temperature
) {
    try {
        const char * model_chars = env->GetStringUTFChars(model_path, nullptr);
        const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
        const char * grammar_chars = env->GetStringUTFChars(grammar, nullptr);
        if (!model_chars || !prompt_chars || !grammar_chars) throw std::runtime_error("Unable to read generation request");
        const std::string model_file(model_chars);
        const std::string prompt_text(prompt_chars);
        const std::string grammar_text(grammar_chars);
        env->ReleaseStringUTFChars(model_path, model_chars);
        env->ReleaseStringUTFChars(prompt, prompt_chars);
        env->ReleaseStringUTFChars(grammar, grammar_chars);

        ggml_backend_load_all();
        llama_backend_init();
        const auto model_params = llama_model_default_params();
        llama_model * model = llama_model_load_from_file(model_file.c_str(), model_params);
        if (!model) throw std::runtime_error("Unable to load GGUF model");

        const llama_vocab * vocab = llama_model_get_vocab(model);
        const int prompt_count = -llama_tokenize(vocab, prompt_text.c_str(), prompt_text.size(), nullptr, 0, true, true);
        if (prompt_count <= 0) {
            llama_model_free(model);
            throw std::runtime_error("Unable to tokenize prompt");
        }
        std::vector<llama_token> prompt_tokens(prompt_count);
        if (llama_tokenize(vocab, prompt_text.c_str(), prompt_text.size(), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
            llama_model_free(model);
            throw std::runtime_error("Unable to tokenize prompt");
        }

        auto context_params = llama_context_default_params();
        // The prompt is decoded as one batch below. llama.cpp aborts the whole
        // process when batch.n_tokens exceeds n_batch, so these values must
        // remain consistent (the old fixed 512 limit caused SIGABRT on real
        // analytics prompts).
        context_params.n_ctx = std::max(2048, prompt_count + static_cast<int>(max_tokens) + 64);
        context_params.n_batch = prompt_count;
        context_params.n_ubatch = prompt_count;
        const auto threads = std::max(2u, std::thread::hardware_concurrency() > 2 ? std::thread::hardware_concurrency() - 2 : 2u);
        context_params.n_threads = threads;
        context_params.n_threads_batch = threads;
        llama_context * context = llama_init_from_model(model, context_params);
        if (!context) {
            llama_model_free(model);
            throw std::runtime_error("Unable to create inference context");
        }

        llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        if (!sampler) {
            llama_free(context);
            llama_model_free(model);
            throw std::runtime_error("Unable to create sampler");
        }
        try {
            if (!grammar_text.empty()) {
                llama_sampler * grammar_sampler = llama_sampler_init_grammar(vocab, grammar_text.c_str(), "root");
                if (!grammar_sampler) throw std::runtime_error("Unable to create grammar sampler");
                llama_sampler_chain_add(sampler, grammar_sampler);
            }
            llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
            llama_sampler_chain_add(sampler, llama_sampler_init_dist(1234));

            llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
            std::string result;
            for (int generated = 0; generated < max_tokens; ++generated) {
                if (llama_decode(context, batch) != 0) throw std::runtime_error("Model evaluation failed");
                llama_token token = llama_sampler_sample(sampler, context, -1);
                if (llama_vocab_is_eog(vocab, token)) break;
                result += token_piece(vocab, token);
                batch = llama_batch_get_one(&token, 1);
            }
            llama_sampler_free(sampler);
            llama_free(context);
            llama_model_free(model);
            return env->NewStringUTF(result.c_str());
        } catch (...) {
            llama_sampler_free(sampler);
            llama_free(context);
            llama_model_free(model);
            throw;
        }
    } catch (const std::exception & error) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", error.what());
        throw_java(env, error.what());
        return nullptr;
    }
}
