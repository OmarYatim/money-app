package com.moneyapp.backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

  @Test
  void contextLoads() {
    assertTrue(false, "Intentional CI failure to verify required checks");
  }
}
