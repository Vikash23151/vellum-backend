package com.vellum.media;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@SpringBootTest
@ActiveProfiles("test")
class MediaServiceApplicationTest {

    /*
     * Mock S3Client so the app doesn't try to connect
     * to real AWS during context load test.
     * @MockBean replaces the real S3Client bean with a mock.
     */
    @MockBean
    private S3Client s3Client;

    @MockBean
    private S3Presigner s3Presigner;

    @Test
    void contextLoads() {
    }
}