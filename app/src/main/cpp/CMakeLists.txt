cmake_minimum_required(VERSION 3.22.1)
project(sieve_hash)

add_library(sieve_hash SHARED sieve_hash.c)

target_compile_options(sieve_hash PRIVATE
    -O3
    -march=armv8-a
    -mtune=cortex-a76
)

target_link_libraries(sieve_hash log)
