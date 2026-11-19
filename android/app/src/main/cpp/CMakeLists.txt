cmake_minimum_required(VERSION 3.22.1)
project(gameforge_llama LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)
set(LLAMA_CPP_PIN "c406f4367e19bea7930e7f74e1984c9acc6d44bf" CACHE STRING "Pinned llama.cpp source revision")

# Runtime code is compiled into the APK. No HTTP server or INTERNET permission is used.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)

FetchContent_Declare(llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG ${LLAMA_CPP_PIN}
    GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(gameforge_llama SHARED native-lib.cpp)
target_include_directories(gameforge_llama PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
)
target_compile_options(gameforge_llama PRIVATE -Wall -Wextra -Wno-deprecated-declarations)
target_link_libraries(gameforge_llama PRIVATE llama android log)
