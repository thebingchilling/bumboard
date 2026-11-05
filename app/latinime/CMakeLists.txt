# Lychee: all native code of the app.
cmake_minimum_required(VERSION 3.24)
project(lychee_native CXX)

# LatinIME's dictionary and suggestion engine (libjni_latinime.so).
add_subdirectory(native/jni)
# Rime bridge (liblycheerime.so).
add_subdirectory(lychee/cpp)
