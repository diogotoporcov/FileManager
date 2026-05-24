package com.filemanager.api;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class FileManagerApiApplicationTests {

    @MockitoBean
    private MinioClient minioClient;

	@Test
	void contextLoads() {
	}

}
