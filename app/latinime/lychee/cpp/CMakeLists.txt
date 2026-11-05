# Lychee: liblycheerime.so, the JNI bridge to librime.
# librime and its dependencies come prebuilt from fcitx5-android/prebuilt (fetched by CI
# into lychee/prebuilt; see .github/workflows/build-lychee.yml).

set(P "${LYCHEE_PREBUILT_DIR}")
if(NOT EXISTS "${P}/librime/${ANDROID_ABI}/lib/librime.a")
    message(FATAL_ERROR "librime prebuilt not found in ${P}; run lychee/scripts/fetch-prebuilt.sh")
endif()

add_library(Rime_static STATIC IMPORTED)
set_target_properties(Rime_static PROPERTIES
    IMPORTED_LOCATION "${P}/librime/${ANDROID_ABI}/lib/librime.a"
    INTERFACE_INCLUDE_DIRECTORIES "${P}/librime/${ANDROID_ABI}/include"
    INTERFACE_LINK_LIBRARIES
        "log;${P}/glog/${ANDROID_ABI}/lib/libglog.a;${P}/leveldb/${ANDROID_ABI}/lib/libleveldb.a;${P}/lua/${ANDROID_ABI}/lib/liblua_static.a;${P}/marisa/${ANDROID_ABI}/lib/libmarisa.a;${P}/opencc/${ANDROID_ABI}/lib/libopencc.a;${P}/yaml-cpp/${ANDROID_ABI}/lib/libyaml-cpp.a")

add_library(lycheerime SHARED lychee_rime.cpp)
target_compile_features(lycheerime PRIVATE cxx_std_17)
# Whole archive so librime's self-registering modules are kept by the linker.
target_link_libraries(lycheerime PRIVATE "$<LINK_LIBRARY:WHOLE_ARCHIVE,Rime_static>" log)
target_link_options(lycheerime PRIVATE -Wl,-z,max-page-size=16384)

# libbumzinnia.so: handwriting recognition with Zinnia (BSD-3-Clause), fetched by CI into
# prebuilt/zinnia (scripts/fetch-zinnia.sh). Models come as packs and are memory-mapped.
set(Z "${LYCHEE_PREBUILT_DIR}/zinnia/zinnia")
if(NOT EXISTS "${Z}/recognizer.cpp")
    message(FATAL_ERROR "Zinnia sources not found in ${Z}; run scripts/fetch-zinnia.sh")
endif()
set(ZINNIA_CONFIG_DIR "${CMAKE_CURRENT_BINARY_DIR}/zinnia-config")
file(WRITE "${ZINNIA_CONFIG_DIR}/config.h" "
#define HAVE_CTYPE_H 1
#define HAVE_FCNTL_H 1
#define HAVE_MMAP 1
#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_SYS_MMAN_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_UNISTD_H 1
#define PACKAGE \"zinnia\"
#define VERSION \"0.06\"
")
add_library(lycheezinnia SHARED
    lychee_zinnia.cpp
    ${Z}/character.cpp
    ${Z}/feature.cpp
    ${Z}/libzinnia.cpp
    ${Z}/param.cpp
    ${Z}/recognizer.cpp
    ${Z}/sexp.cpp
    ${Z}/svm.cpp
    ${Z}/trainer.cpp)
target_include_directories(lycheezinnia PRIVATE "${ZINNIA_CONFIG_DIR}" "${Z}")
target_compile_definitions(lycheezinnia PRIVATE HAVE_CONFIG_H=1)
# Zinnia is C++98-era code: keep an older standard and quiet its warnings.
target_compile_options(lycheezinnia PRIVATE -std=gnu++11 -w -fexceptions)
target_link_libraries(lycheezinnia PRIVATE log)
target_link_options(lycheezinnia PRIVATE -Wl,-z,max-page-size=16384)
