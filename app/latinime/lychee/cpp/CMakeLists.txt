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
