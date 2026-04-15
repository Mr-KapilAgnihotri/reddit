package com.kapil.reddit;

import com.kapil.reddit.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Smoke test — verifies the Spring application context loads successfully
 * with all real infrastructure (Postgres, Redis, EmbeddedKafka) wired up.
 *
 * JavaMailSender is mocked so no SMTP server is required.
 * KafkaTemplate uses the EmbeddedKafka broker provided by AbstractIntegrationTest.
 */
class RedditApplicationTests extends AbstractIntegrationTest {

    @MockBean
    private JavaMailSender javaMailSender;

    @Test
    void contextLoads() {
        // Context load success = test passes.
    }
}
