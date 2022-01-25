import javax.swing.*;
import java.awt.*;
import java.awt.image.MemoryImageSource;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Perlin2D {

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 2048;
    private static final int MIN_I = 1;
    private static final int MAX_I = 1000;
    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 32;

    // Cartesian values of the screen
    public static final double WIDTH = 15.0;
    public static final double HEIGHT = 15.0;

    // parameters of our thread models
    private static final int BLOCK_SIZE = 2;

    // global values
    public static int[] BUFFER;
    public static int SIZE;

    public static void main(String[] args) {

        System.out.println("Perlin Noise Speedup Tester");

        // Make sure we have the right number of arguments
        if (args.length != 4)
        {
            printUsage("Must have 4 command line arguments.");
            System.exit(1);
        }

        // Parse and check the arguments.
        int numberOfImages, numberOfThreads, workDistModel;
        try
        {
            SIZE = parseInt(args[0], "size", MIN_SIZE, MAX_SIZE);
            System.out.println("\tSIZE: " + args[0]);
            numberOfImages = parseInt(args[1], "images", MIN_I, MAX_I);
            System.out.println("\t# IMAGES: " + args[1]);
            numberOfThreads = parseInt(args[2], "threads", MIN_THREADS, MAX_THREADS);
            System.out.println("\t# THREADS: " + args[2]);
            workDistModel = parseInt(args[3], "model", 1, 6);
            System.out.println("\tTHREADING MODEL: " + (numberOfThreads == 1 ? "N/A" : args[3]));
        } catch (NumberFormatException ex)
        {
            printUsage(ex.getMessage());
            System.exit(2);
            return; // so java knows variables have been initialized
        }

        // Make space for the image
        BUFFER = new int[SIZE * SIZE];

        // Start clock
        final Stopwatch watch = new Stopwatch();

        for(int imageNum = 0; imageNum < numberOfImages; imageNum++){

            // use multi-threading
            if(numberOfThreads > 1){

                // if needed, create lists of items (rows, blocks, pixels) and pass them
                // to the worker threads.
                int numItems;
                // next free row
                if(workDistModel == 4){
                    numItems = SIZE;
                }
                // next free pixel
                else if(workDistModel == 5){
                    numItems = SIZE*SIZE;
                }
                // next free block
                else if(workDistModel == 6){
                    numItems = SIZE / BLOCK_SIZE;
                }

                else numItems = 0;

                ConcurrentLinkedQueue<Integer> items = null;
                if(numItems > 0){
                    items = new ConcurrentLinkedQueue<>();
                    for(int i = 0; i < numItems; i++){
                        items.add(i);
                    }
                }

                final List<Thread> perlinDrawingThreads = new LinkedList<>();
                for (int threadNumber = 0; threadNumber < numberOfThreads; threadNumber++)
                {

                    ThreadedPerlinDrawer runner;
                    // X-STRIDE TYPE MODELS
                    if(workDistModel == 1 || workDistModel == 2 || workDistModel == 3){
                        runner = new PerlinStride(numberOfThreads, threadNumber, workDistModel);
                    }
                    // NEXT-FREE-X TYPE MODELS
                    else{
                        runner = new PerlinNextFree(numberOfThreads, threadNumber, workDistModel, items);
                    }

                    Thread t = new Thread(runner);
                    perlinDrawingThreads.add(t);
                    t.start();
                }

                // Wait for the threads to be done
                for (Thread t : perlinDrawingThreads)
                {
                    try
                    {
                        t.join();
                    } catch (InterruptedException ex)
                    {
                        System.err.println("Execution was Interrupted!");
                    }
                }
            }

            // single-threaded
            else{
                PerlinDrawer.runCompleteDraw();
            }

        }

        // Stop the clock
        System.out.printf("Drawing took %f seconds\n", watch.elapsedTime());

        // Show the image
        displayImage();

    }

    // Print usage of the program and argument ranges/choices.
    private static void printUsage(String errorMessage)
    {
        System.err.println(errorMessage);
        System.err.println("The program arguments are:");

        System.err.printf("\tsize: the height and width for the image [%d, %d]\n", MIN_SIZE, MAX_SIZE);
        System.err.printf("\timages: the number of images to generate (only the last is displayed) [%d, %d]\n", MIN_I, MAX_I);
        System.err.printf("\tthreads: the number of threads to use [%d, %d]\n", MIN_THREADS, MAX_THREADS);
        System.err.println("\tmodel: the work distribution model to use when threading [1,6]");
        System.err.println("\t\t1: Row Stride");
        System.err.println("\t\t2: Block Stride");
        System.err.println("\t\t3: Pixel Stride");
        System.err.println("\t\t4: Next Free Row");
        System.err.println("\t\t5: Next Free Pixel");
        System.err.println("\t\t6: Next Free Block");
    }

    // Parse the given string s as an int and check that it is within the given range. If not
    // throw a NumberFormatException.
    private static int parseInt(String s, String name, int min, int max)
    {
        final int result;
        try
        {
            result = Integer.parseInt(s);
        } catch (NumberFormatException ex)
        {
            throw new NumberFormatException(String.format("Value, %s, given for %s is not a number", s, name));
        }

        if (result < min || result > max)
        {
            throw new NumberFormatException(String.format("Value, %d, given for %s is not in the range [%d, %d]",
                    result, name, min, max));
        }

        return result;
    }

    private static void displayImage()
    {
        SwingUtilities.invokeLater(() ->
        {
            // Make a frame
            JFrame f = new JFrame("Perlin Noise");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Add the drawing panel
            DrawingPanel panel = new DrawingPanel();
            f.add(panel);
            panel.setPreferredSize(new Dimension(SIZE, SIZE));
            f.pack();
            f.setResizable(false);
            f.setVisible(true);
        });
    }

    private static class DrawingPanel extends JPanel
    {

        private final Image image;

        public DrawingPanel()
        {
            image = super.createImage(new MemoryImageSource(SIZE, SIZE, BUFFER, 0, SIZE));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, this);
        }
    }

    abstract static class ThreadedPerlinDrawer extends PerlinDrawer implements Runnable{
        volatile boolean running = true;
        final int numberOfThreads, threadIndex;

        ThreadedPerlinDrawer(int numberOfThreads, int threadIndex){
            this.numberOfThreads = numberOfThreads;
            this.threadIndex = threadIndex;
        }

        public void stopRunning()
        {
            running = false;
        }
    }

    // X-STRIDE WORKER MODEL
    private static class PerlinStride extends ThreadedPerlinDrawer
    {
        // Model-specific params
        private final int startingRow;
        private final int strideType;

        public PerlinStride(int numberOfThreads, int startingRow, int strideType)
        {
            super(numberOfThreads, startingRow);
            this.startingRow = startingRow;
            this.strideType = strideType;
        }

        // The drawing code
        @Override
        public void run()
        {
            // ROW STRIDE
            if(strideType == 1){
                // Keep drawing rows as long as we are not done and are still running
                for (int row = startingRow; running && row < SIZE; row += numberOfThreads)
                {
                    for (int column = 0; column < SIZE; column++)
                    {
                        computeSavePerlinColor(column, row);
                    }
                }
            }

            // BLOCK STRIDE
            else if(strideType == 2){
                int num_blocks = SIZE / BLOCK_SIZE;
                for(int block = threadIndex; running && block < num_blocks; block += numberOfThreads){
                    // draw rows in this block
                    int start_row = block * BLOCK_SIZE;
                    int end_row = Math.min((start_row + BLOCK_SIZE), SIZE);
                    for (int row = start_row; row < end_row; row ++)
                    {
                        for (int column = 0; column < SIZE; column++)
                        {
                            computeSavePerlinColor(column, row);
                        }
                    }
                }
            }

            // PIXEL STRIDE
            else if(strideType == 3){
                for(int pixel_id = threadIndex; pixel_id < BUFFER.length && running; pixel_id += numberOfThreads){
                    int col = pixel_id % SIZE;
                    int row = pixel_id / SIZE;

                    computeSavePerlinColor(col, row);
                }
            }

        }
    }

    private static class PerlinNextFree extends ThreadedPerlinDrawer
    {
        private final int itemType;
        private final ConcurrentLinkedQueue<Integer> itemList;

        public PerlinNextFree(int numberOfThreads, int threadNum, int itemType, ConcurrentLinkedQueue<Integer> itemList)
        {
            super(numberOfThreads, threadNum);
            this.itemType = itemType;
            this.itemList = itemList;
        }

        // The drawing code
        @Override
        public void run()
        {
            // NEXT FREE ROW
            if(itemType == 4){
                // itemList contains all rows
                Integer row;
                while((row = itemList.poll()) != null && running){
                    for (int column = 0; column < SIZE; column++)
                    {
                        computeSavePerlinColor(column, row);
                    }
                }
            }

            // NEXT FREE PIXEL
            else if(itemType == 5){
                Integer pixel_id;
                while((pixel_id = itemList.poll()) != null && running){
                    int col = pixel_id % SIZE;
                    int row = pixel_id / SIZE;

                    computeSavePerlinColor(col, row);
                }
            }

            // NEXT FREE BLOCK
            else if(itemType == 6){
                Integer block;
                while((block = itemList.poll()) != null && running){
                    int start_row = block * BLOCK_SIZE;
                    int end_row = Math.min((start_row + BLOCK_SIZE), SIZE);
                    for (int row = start_row; row < end_row; row ++)
                    {
                        for (int column = 0; column < SIZE; column++)
                        {
                            computeSavePerlinColor(column, row);
                        }
                    }
                }
            }

        }
    }

}
