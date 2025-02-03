import com.mycompany.javafxapplication1.LoadBalancerClient;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoadBalancerTest {
    private LoadBalancerClient client;
    private static final String TEST_FILE_PATH = "test_files";
    private Random random = new Random();

    public LoadBalancerTest() {
        // Initialize the test client
        client = new LoadBalancerClient("localhost", 8080);
        
        // Create test directory if it doesn't exist
        new File(TEST_FILE_PATH).mkdirs();
    }

    /**
     * Tests basic file upload and download functionality
     * This verifies the core operations of our system
     */
    public void testBasicFileOperations() {
        try {
            // Create a test file with known content
            String testFileName = "test_file_" + System.currentTimeMillis() + ".txt";
            File testFile = createTestFile(testFileName, 5 * 1024 * 1024); // 5MB file
            
            System.out.println("Starting basic file operations test...");
            System.out.println("Created test file: " + testFile.getAbsolutePath());

            // Upload the file
            String fileId = UUID.randomUUID().toString();
            System.out.println("Generated file ID: " + fileId);
            
            long startTime = System.currentTimeMillis();
            uploadFile(testFile, fileId);
            long uploadDuration = System.currentTimeMillis() - startTime;
            
            System.out.println("Upload completed in " + uploadDuration/1000.0 + " seconds");

            // Download the file to a different location
            File downloadedFile = new File(TEST_FILE_PATH, "downloaded_" + testFileName);
            startTime = System.currentTimeMillis();
            downloadFile(fileId, downloadedFile);
            long downloadDuration = System.currentTimeMillis() - startTime;
            
            System.out.println("Download completed in " + downloadDuration/1000.0 + " seconds");

            // Verify file contents match
            if (compareFiles(testFile, downloadedFile)) {
                System.out.println("✓ File content verification successful");
            } else {
                System.out.println("✗ File content verification failed");
            }

        } catch (Exception e) {
            System.err.println("Test failed with error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Tests concurrent file operations to verify load balancer behavior
     * This ensures our system can handle multiple simultaneous requests
     */
    public void testConcurrentOperations() {
        int numConcurrentOperations = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numConcurrentOperations);
        CountDownLatch latch = new CountDownLatch(numConcurrentOperations);

        System.out.println("Starting concurrent operations test...");
        
        for (int i = 0; i < numConcurrentOperations; i++) {
            executor.submit(() -> {
                try {
                    testBasicFileOperations();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            // Wait for all operations to complete or timeout after 5 minutes
            boolean completed = latch.await(5, TimeUnit.MINUTES);
            if (completed) {
                System.out.println("✓ Concurrent operations test completed successfully");
            } else {
                System.out.println("✗ Concurrent operations test timed out");
            }
        } catch (InterruptedException e) {
            System.err.println("Concurrent test interrupted: " + e.getMessage());
        }
        
        executor.shutdown();
    }

    // Helper methods
    private File createTestFile(String fileName, int size) throws IOException {
        File file = new File(TEST_FILE_PATH, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int remaining = size;
            while (remaining > 0) {
                random.nextBytes(buffer);
                fos.write(buffer, 0, Math.min(buffer.length, remaining));
                remaining -= buffer.length;
            }
        }
        return file;
    }

    private void uploadFile(File file, String fileId) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[1024 * 1024]; // 1MB chunks
        int chunkNumber = 0;
        int bytesRead;

        while ((bytesRead = fis.read(buffer)) != -1) {
            byte[] chunk = bytesRead < buffer.length ? Arrays.copyOf(buffer, bytesRead) : buffer;
            try {
                String containerId = client.uploadFileChunk(fileId, chunkNumber, chunk);
                System.out.println("Chunk " + chunkNumber + " uploaded to container " + containerId);
                chunkNumber++;
            } catch (ClassNotFoundException e) {
                throw new IOException("Error uploading chunk", e);
            }
        }
    }

    private void downloadFile(String fileId, File outputFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            int chunkNumber = 0;
            while (true) {
                try {
                    byte[] chunk = client.downloadFileChunk(fileId, chunkNumber);
                    if (chunk == null || chunk.length == 0) break;
                    fos.write(chunk);
                    System.out.println("Chunk " + chunkNumber + " downloaded");
                    chunkNumber++;
                } catch (ClassNotFoundException | IOException e) {
                    try {
                        if (chunkNumber == 0) throw e;
                        break;
                    } catch (ClassNotFoundException ex) {
                        Logger.getLogger(LoadBalancerTest.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
    }

    private boolean compareFiles(File file1, File file2) throws IOException {
        if (file1.length() != file2.length()) return false;

        try (FileInputStream fis1 = new FileInputStream(file1);
             FileInputStream fis2 = new FileInputStream(file2)) {
            
            byte[] buffer1 = new byte[8192];
            byte[] buffer2 = new byte[8192];
            int bytesRead1;
            
            while ((bytesRead1 = fis1.read(buffer1)) != -1) {
                int bytesRead2 = fis2.read(buffer2);
                if (bytesRead1 != bytesRead2) return false;
                
                for (int i = 0; i < bytesRead1; i++) {
                    if (buffer1[i] != buffer2[i]) return false;
                }
            }
            return true;
        }
    }

    public static void main(String[] args) {
        LoadBalancerTest test = new LoadBalancerTest();
        
        System.out.println("=== Starting Load Balancer System Tests ===\n");
        
        System.out.println("1. Testing Basic File Operations");
        test.testBasicFileOperations();
        
        System.out.println("\n2. Testing Concurrent Operations");
        test.testConcurrentOperations();
        
        System.out.println("\n=== Test Suite Completed ===");
    }
}