package com.RevHire.benchmark;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import com.RevHire.benchmark.support.BenchSupport;
import com.RevHire.benchmark.support.ResumeFixture;
import com.RevHire.service.impl.ResumeServiceImpl;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Misura il secondo passo della funzionalità di download di un CV creato sull'app.
 *
 * Conversione HTML -> PDF, con lo stesso codice di ResumeController (PdfRendererBuilder).
 * L'HTML viene generato una sola volta nel @Setup: qui si misura solo la conversione.
 * Il costo totale di "scarica il CV" e' circa ResumeHtmlBenchmark + questo benchmark.
 * Valori di itemsPerSection piu' bassi del benchmark HTML, perche' il PDF e' molto piu' lento.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class ResumePdfBenchmark {

    @Param({"5", "25", "100"})
    public int itemsPerSection;

    private String html;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        BenchSupport.silenceLogging();
        // openhtmltopdf scrive una riga sulla console a ogni conversione: la spegniamo,
        // altrimenti la scrittura su console entra nel tempo misurato.
        XRLog.listRegisteredLoggers().forEach(name -> XRLog.setLevel(name, java.util.logging.Level.OFF));

        ResumeServiceImpl service = ResumeFixture.serviceWith(itemsPerSection);
        html = service.generateResumeHtml(ResumeFixture.RESUME_ID);

        // Controllo di correttezza: il risultato deve essere un PDF
        byte[] pdf = htmlToPdf();
        if (pdf.length < 5 || !new String(pdf, 0, 4, StandardCharsets.ISO_8859_1).equals("%PDF")) {
            throw new IllegalStateException("Il risultato non e' un PDF valido");
        }
    }

    @Benchmark
    public byte[] htmlToPdf() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }
}