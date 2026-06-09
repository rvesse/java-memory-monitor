import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Random;

public final class Loiter {
    public static void main(String[] args) throws InterruptedException {
        ByteBuffer buffer;
        Random random = new Random();
        int iteration = 0;
        while (true) {
            System.out.println("[" + Instant.now().toString() + "] Loitering...");
            // Randomly allocate a buffer of between 32 and 128MB
            int mb = random.nextInt(32, 129);
            System.out.println("[" + Instant.now().toString() + "] Allocated a " + mb + "MB direct byte buffer");
            buffer = ByteBuffer.allocateDirect(mb * 1024 * 1024);

            // Hold the buffer for a while
            Thread.sleep(30000);

            // Free the buffer
            buffer = null;

            // Force a GC once in a while
            iteration++;
            if (iteration % 10 == 0) {
                System.out.println("[" + Instant.now().toString() + "] Forcing a GC");
                System.gc();
            }
        }
    }
}