import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;

public final class Loiter {

    /**
     * A toy reference type
     */
    private static final class Data {
        private final String string;
        private final int number;
        private final boolean bool;

        public Data(String string, int number, boolean bool) {
            this.string = string;
            this.number = number;
            this.bool = bool;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ByteBuffer buffer;
        Random random = new Random();
        int iteration = 0;
        while (true) {
            System.out.println("[" + Instant.now().toString() + "] Loitering...");
            
            // Randomly allocate a bunch of on-heap objects in an array
            List<Object> data = new ArrayList<>();
            int toAllocate = random.nextInt(10_000, 100_000);
            for (int i = 1; i <= toAllocate; i++) {
                data.add(switch (i % 5) {
                    case 0 -> Integer.toString(i, 16).toUpperCase();
                    case 1 -> i;
                    case 2 -> true;
                    case 3 -> false;
                    case 4 -> new Data(Integer.toString(i, 16).toUpperCase(), i, i % 3 == 0);
                    default -> null;
                });
            }
            System.out.println("[" + Instant.now().toString() + "] Allocated " + String.format("%,d", data.size()) + " objects on the heap");
            
            // Randomly allocate an off-heap buffer of between 32 and 128MB
            int mb = random.nextInt(32, 129);
            System.out.println("[" + Instant.now().toString() + "] Allocated an " + mb + "MB direct byte buffer");
            buffer = ByteBuffer.allocateDirect(mb * 1024 * 1024);

            // Hold the buffer for a while
            Thread.sleep(30000);

            // Free the buffer and the allocated heap objects
            buffer = null;
            data.clear();
            data = null;

            // Force a GC once in a while
            iteration++;
            if (iteration % 10 == 0) {
                System.out.println("[" + Instant.now().toString() + "] Forcing a GC");
                System.gc();
            }
        }
    }
}