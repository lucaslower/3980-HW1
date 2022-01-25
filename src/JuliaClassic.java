import java.util.LinkedList;
import java.util.List;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.geom.Point2D;
import java.awt.image.MemoryImageSource;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class JuliaClassic
{

    private static final double MIN_A = -1.0;
    private static final double MAX_A = 1.0;
    private static final double MIN_B = -1.0;
    private static final double MAX_B = 1.0;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 2048;
    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 32;

    // Cartesian values of the screen
    private static final double CENTER_X = 0.0;
    private static final double CENTER_Y = 0.0;
    private static final double WIDTH = 3.25;
    private static final double HEIGHT = 3.25;

    // Maximum number of iterations before a number is declared in the Julia set
    public static final int MAX_ITERATIONS = 100;

    // Distance from beyond which a point is not in the set
    public static final double THRESHOLD = 2.0;

    public static void main(String[] args)
    {
        // Make sure we have the right number of arguments
        if (args.length != 4)
        {
            printUsage("Must have 4 command line arguments.");
            System.exit(1);
        }

        // Parse and check the arguments.
        double a, b;
        int size, numberOfThreads;
        try
        {
            a = parseDouble(args[0], "a", MIN_A, MAX_A);
            b = parseDouble(args[1], "b", MIN_B, MAX_B);
            size = parseInt(args[2], "size", MIN_SIZE, MAX_SIZE);
            numberOfThreads = parseInt(args[3], "threads", MIN_THREADS, MAX_THREADS);
        } catch (NumberFormatException ex)
        {
            printUsage(ex.getMessage());
            System.exit(2);
            return; // so java knows variables have been initialized
        }

        // Make space for the image
        int[] imageData = new int[size * size];

        // Start clock
        final Stopwatch watch = new Stopwatch();

        // Make a threads for drawing. The values are passed to the
        // constructor but we could have made them global.
        final List<JuliaDrawingThread> juliaDrawingThreads = new LinkedList<>();
        for (int threadNumber = 0; threadNumber < numberOfThreads; threadNumber++)
        {
            JuliaDrawingThread thread = new JuliaDrawingThread(imageData, a, b, threadNumber, numberOfThreads, size);
            juliaDrawingThreads.add(thread);
            thread.start();
        }

        // Wait for the threads to be done
        for (JuliaDrawingThread t : juliaDrawingThreads)
        {
            try
            {
                t.join();
            } catch (InterruptedException ex)
            {
                System.err.println("Execution was Interrupted!");
            }
        }

        // Stop the clock
        System.out.printf("Drawing took %f seconds\n", watch.elapsedTime());

        // Show the image
        displayImage(imageData, size);
    }

    // Print a given message and some basic usage infomation
    private static void printUsage(String errorMessage)
    {
        System.err.println(errorMessage);
        System.err.println("The program arguments are:");
        System.err.printf("\ta: the Julia set's a constant [%f, %f]\n", MIN_A, MAX_A);
        System.err.printf("\tb: the Julia set's b constant [%f, %f]\n", MIN_B, MAX_B);
        System.err.printf("\tsize: the height and width for the image [%d, %d]\n", MIN_SIZE, MAX_SIZE);
        System.err.printf("\tthreads: the number of threads to use [%d, %d]\n", MIN_THREADS, MAX_THREADS);
    }

    // Parse the given string s as a double and check that it is within the given range. If not
    // throw a NumberFormatException.
    private static double parseDouble(String s, String name, double min, double max)
    {
        final double result;
        try
        {
            result = Double.parseDouble(s);
        } catch (NumberFormatException ex)
        {
            throw new NumberFormatException(String.format("Value, %s, given for %s is not a number", s, name));
        }

        if (result < min || result > max)
        {
            throw new NumberFormatException(String.format("Value, %f, given for %s is not in the range [%f, %f]",
                    result, name, min, max));
        }

        return result;
    }

    // Parse the given string s as a int and check that it is within the given range. If not
    // throw a NumberFormatException. Very simlaer to parseDouble but I did not think it was
    // worth refactoring.
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

    private static void displayImage(int[] imageData, int size)
    {
        SwingUtilities.invokeLater(() ->
        {
            // Make a frame
            JFrame f = new JFrame("Juila Set");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Add the drawing panel
            DrawingPanel panel = new DrawingPanel(imageData, size);
            f.add(panel);
            panel.setPreferredSize(new Dimension(size, size));
            f.pack();
            f.setResizable(false);
            f.setVisible(true);
        });
    }

    // Return the color a given Cartesian point should be colored. Black if it is
    // in the Julia Set. Some other color if it is not.
    private static int juliaColor(double x, double y, double a, double b)
    {
        // Going to be using a smooth coloring function in the HSV color space.
        // The color variable will be used to pick the hue.
        float color = 0;

        // What iteration we are on
        int i = 0;
        double distance = distance(x, y);

        // While we have not left the bounds and there are still iterations to go.
        // Note the test also increments i.
        while (distance < THRESHOLD && i++ < MAX_ITERATIONS)
        {
            // Apply the Julia Map
            double nextX = x * x - y * y + a;
            y = 2.0 * x * y + b;
            x = nextX;

            // Update distance
            distance = distance(x, y);

            // Update the color. See
            // http://en.wikipedia.org/wiki/Mandelbrot_set#Continuous_.28smooth.29_coloring
            // or other such references on smooth coloring functions.
            color += Math.exp(-distance);
        }

        // If we are still within the bounds the point is in the set
        if (distance < THRESHOLD)
        {
            return Color.black.getRGB();
        }

        // Otherwise convert the hue into a color object
        return Color.getHSBColor(0.5f + 10 * color / MAX_ITERATIONS, 1.0f, 1.0f).getRGB();
    }

    private static double distance(double x, double y)
    {
        return distance(x, y, 0, 0);
    }

    private static double distance(double x1, double y1, double x2, double y2)
    {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    // Convert the given point (x, y) in graphics coordinates into Cartesian
    // coordinates. This is just a linear transformation.
    private static Point2D.Double convertScreenToCartesian(double x, double y, int screenWidth, int screenHeight)
    {
        return new Point2D.Double(WIDTH / screenWidth * x + CENTER_X - WIDTH / 2.0,
                -HEIGHT / screenHeight * y + CENTER_Y + HEIGHT / 2.0);
    }

    // A thread for drawing Julia sets, does what it says...
    private static class JuliaDrawingThread extends Thread
    {

        // This thread does not enter into any monitors so calling stop is safe.
        // However, deferred cancellation is still the preferred way of stopping
        // threads. This data member will keep track of if the thread should still
        // be running. It has the volatile keyword to let Java know that it could
        // be modified by another thread.
        private volatile boolean running = true;

        // Copies of the values used for drawing
        private final double a, b;
        private final int startingRow, numberOfThreads;
        private final int[] buffer;
        private final int size;

        public JuliaDrawingThread(int[] buffer, double a, double b, int startingRow, int numberOfThreads, int size)
        {
            super("Julia Drawing Thread: " + startingRow + "/" + numberOfThreads);
            this.buffer = buffer;
            this.a = a;
            this.b = b;
            this.startingRow = startingRow;
            this.numberOfThreads = numberOfThreads;
            this.size = size;
        }

        public void stopRunning()
        {
            running = false;
        }

        // The drawing code
        @Override
        public void run()
        {
            // Keep drawing rows as long as we are not done and are still running
            for (int row = startingRow; running && row < size; row += numberOfThreads)
            {
                for (int column = 0; column < size; column++)
                {
                    final Point2D.Double cartesianPoint = convertScreenToCartesian(column, row, size, size);

                    buffer[row * size + column] = juliaColor(cartesianPoint.getX(), cartesianPoint.getY(), a, b);
                }
            }
        }
    }

    private static class DrawingPanel extends JPanel
    {

        private final Image image;

        public DrawingPanel(int[] imageData, int size)
        {
            image = super.createImage(new MemoryImageSource(size, size, imageData, 0, size));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, this);
        }
    }
}

