package com.RevHire.benchmark;

import com.RevHire.benchmark.support.BenchSupport;
import com.RevHire.benchmark.support.ResumeFixture;
import com.RevHire.service.impl.ResumeServiceImpl;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Misura il primo passo della funzionalità di download di un CV creato sull'app (il CV viene tradotto in htmlper poi essere tradotto in pdf)
 *
 * Generazione dell'HTML del CV (ResumeServiceImpl.generateResumeHtml).
 * Variabile: numero di elementi in ciascuna delle 5 sezioni (totale = 5 x itemsPerSection).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class ResumeHtmlBenchmark {

    @Param({"5", "50", "500"})
    public int itemsPerSection;

    private ResumeServiceImpl service;

    @Setup(Level.Trial)
    public void setUp() {
        BenchSupport.silenceLogging();
        service = ResumeFixture.serviceWith(itemsPerSection);

        // Controllo di correttezza: l'HTML deve contenere tutti gli elementi attesi
        String html = service.generateResumeHtml(ResumeFixture.RESUME_ID);
        int paragraphs = BenchSupport.countOccurrences(html, "<p");
        if (paragraphs != ResumeFixture.expectedParagraphs(itemsPerSection)) {
            throw new IllegalStateException("HTML inatteso: " + paragraphs + " paragrafi, attesi "
                    + ResumeFixture.expectedParagraphs(itemsPerSection));
        }
    }

    @Benchmark
    public String generateResumeHtml() {
        return service.generateResumeHtml(ResumeFixture.RESUME_ID);
    }
}