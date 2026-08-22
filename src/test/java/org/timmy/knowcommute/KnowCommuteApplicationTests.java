package org.timmy.knowcommute;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class KnowCommuteApplicationTests {

    @Test
    void contextLoads() {
    }

}
