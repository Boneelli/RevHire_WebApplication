package com.RevHire.benchmark;

import com.RevHire.benchmark.support.BenchSupport;
import com.RevHire.entity.Role;
import com.RevHire.entity.User;
import com.RevHire.repository.UserRepository;
import com.RevHire.service.impl.AuthServiceImpl;
import org.openjdk.jmh.annotations.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.RevHire.benchmark.support.BenchSupport.stub;

/**
 * Misura le funzionalità di Login e registrazione (AuthServiceImpl) con BCrypt.
 * L'applicazione usa BCryptPasswordEncoder con strength 10 (default): e' il valore "reale".
 * Il valore 12 mostra quanto costa un hashing piu' sicuro.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(3)
public class PasswordBenchmark {

    private static final String EMAIL = "mario.rossi@bench.test";
    private static final String PASSWORD = "password123";

    @Param({"10", "12"})
    public int strength;

    private AuthServiceImpl authService;

    @Setup(Level.Trial)
    public void setUp() {
        BenchSupport.silenceLogging();

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(strength);
        User stored = newUser(encoder.encode(PASSWORD));

        UserRepository userRepository = stub(UserRepository.class)
                .returns("findByEmail", Optional.of(stored))
                .returns("existsByEmail", false)
                .answers("save", args -> args[0])
                .build();

        authService = new AuthServiceImpl(userRepository, encoder);

        // Controllo di correttezza: il login con la password giusta deve riuscire
        if (authService.login(EMAIL, PASSWORD) == null) {
            throw new IllegalStateException("Login non riuscito nel setup");
        }
    }

    /** Verifica della password: BCrypt matches(). */
    @Benchmark
    public User login() {
        return authService.login(EMAIL, PASSWORD);
    }

    /** Registrazione: BCrypt encode() + salvataggio (finto). */
    @Benchmark
    public User registerUser() {
        return authService.registerUser(newUser(PASSWORD));
    }

    private static User newUser(String passwordHash) {
        return User.builder()
                .userId(1L)
                .email(EMAIL)
                .passwordHash(passwordHash)
                .role(Role.JOB_SEEKER)
                .securityQuestion("domanda")
                .securityAnswerHash("risposta")
                .build();
    }
}