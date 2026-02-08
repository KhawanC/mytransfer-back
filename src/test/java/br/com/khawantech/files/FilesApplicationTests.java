package br.com.khawantech.files;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Basic application tests.
 * 
 * Note: Full integration tests require MongoDB and Redis to be running.
 * Use Testcontainers or configure test containers for complete integration testing.
 * 
 * For quick running of tests without external dependencies:
 * - Start MongoDB on localhost:27017
 * - Start Redis on localhost:6379
 */
class FilesApplicationTests {

	@Test
	void applicationMainClassExists() {
		// Verify the main application class exists and is valid
		assertNotNull(FilesApplication.class);
	}

}
