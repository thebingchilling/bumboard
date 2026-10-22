# BumBoard: all native code of the app.
cmake_minimum_required(VERSION 3.24)
project(bumboard_native CXX)

# LatinIME's dictionary and suggestion engine (libjni_latinime.so).
add_subdirectory(native/jni)
# Rime bridge (libbumrime.so).
add_subdirectory(bumboard/cpp)
