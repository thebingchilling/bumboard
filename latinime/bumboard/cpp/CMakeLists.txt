# BumBoard: libbumrime.so, the JNI bridge to librime.
# librime and its dependencies come prebuilt from fcitx5-android/prebuilt (fetched by CI
# into bumboard/prebuilt; see .github/workflows/build-bumboard.yml).

set(P "${BUMBOARD_PREBUILT_DIR}")
if(NOT EXISTS "${P}/librime/${ANDROID_ABI}/lib/librime.a")
    message(FATAL_ERROR "librime prebuilt not found in ${P}; run bumboard/scripts/fetch-prebuilt.sh")
endif()

add_library(Rime_static STATIC IMPORTED)
set_target_properties(Rime_static PROPERTIES
    IMPORTED_LOCATION "${P}/librime/${ANDROID_ABI}/lib/librime.a"
    INTERFACE_INCLUDE_DIRECTORIES "${P}/librime/${ANDROID_ABI}/include"
    INTERFACE_LINK_LIBRARIES
        "log;${P}/glog/${ANDROID_ABI}/lib/libglog.a;${P}/leveldb/${ANDROID_ABI}/lib/libleveldb.a;${P}/lua/${ANDROID_ABI}/lib/liblua_static.a;${P}/marisa/${ANDROID_ABI}/lib/libmarisa.a;${P}/opencc/${ANDROID_ABI}/lib/libopencc.a;${P}/yaml-cpp/${ANDROID_ABI}/lib/libyaml-cpp.a")

add_library(bumrime SHARED bum_rime.cpp)
target_compile_features(bumrime PRIVATE cxx_std_17)
# Whole archive so librime's self-registering modules are kept by the linker.
target_link_libraries(bumrime PRIVATE "$<LINK_LIBRARY:WHOLE_ARCHIVE,Rime_static>" log)
target_link_options(bumrime PRIVATE -Wl,-z,max-page-size=16384)
