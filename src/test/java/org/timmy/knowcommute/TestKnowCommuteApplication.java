package org.timmy.knowcommute;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally with a Testcontainers-backed Postgres,
 * so no local database install is needed.
 */
public class TestKnowCommuteApplication {

    public static void main(String[] args) {
        SpringApplication.from(KnowCommuteApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }

}
