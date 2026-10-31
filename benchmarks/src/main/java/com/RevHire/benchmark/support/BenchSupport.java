package com.RevHire.benchmark.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utilita' condivise dai benchmark che non avviano Spring:
 *  - silenceLogging(): spegne i log (altrimenti ogni chiamata scrive sulla console e falsa i tempi)
 *  - stub(...): crea un repository finto (proxy Java) con risposte fisse. E' molto piu' leggero
 *    di Mockito, quindi il costo dei finti repository resta trascurabile rispetto al codice misurato.
 */
public final class BenchSupport {

    private BenchSupport() {
    }

    public static void silenceLogging() {
        // I servizi usano log4j-api, che in questo jar passa da SLF4J a Logback.
        Logger root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root instanceof ch.qos.logback.classic.Logger logback) {
            logback.setLevel(ch.qos.logback.classic.Level.OFF);
        }
        // openhtmltopdf usa java.util.logging
        java.util.logging.LogManager.getLogManager().reset();
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.OFF);
    }

    public static <T> RepositoryStub<T> stub(Class<T> repositoryType) {
        return new RepositoryStub<>(repositoryType);
    }

    public static int countOccurrences(String text, String token) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(token, from)) >= 0) {
            count++;
            from += token.length();
        }
        return count;
    }

    public static final class RepositoryStub<T> {
        private final Class<T> type;
        private final Map<String, Function<Object[], Object>> answers = new HashMap<>();

        private RepositoryStub(Class<T> type) {
            this.type = type;
        }

        /** Il metodo restituisce sempre lo stesso valore. */
        public RepositoryStub<T> returns(String methodName, Object value) {
            answers.put(methodName, args -> value);
            return this;
        }

        /** Il metodo calcola il risultato a partire dagli argomenti. */
        public RepositoryStub<T> answers(String methodName, Function<Object[], Object> answer) {
            answers.put(methodName, answer);
            return this;
        }

        public T build() {
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> type.getSimpleName() + " (stub)";
                    };
                }
                Function<Object[], Object> answer = answers.get(method.getName());
                if (answer == null) {
                    throw new UnsupportedOperationException(
                            "Metodo non previsto nello stub di " + type.getSimpleName() + ": " + method.getName());
                }
                return answer.apply(args);
            };
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }
    }
}