package com.RevHire.benchmark;

import org.openjdk.jmh.annotations.Benchmark;

public class MyBenchmark {

    @Benchmark
    public int test() {
        return "hello".length();
    }
}
