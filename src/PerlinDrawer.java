import java.awt.*;
import java.awt.geom.Point2D;

import static java.lang.Math.*;

public class PerlinDrawer{

    // Convert the given point (x, y) in graphics coordinates into Cartesian
    // coordinates (we only support showing the first quadrant).
    static Point2D.Double convertScreenToCartesian(double x, double y, int screenWidth, int screenHeight)
    {
        return new Point2D.Double(Perlin2D.WIDTH / screenWidth * x,
                Perlin2D.HEIGHT - (Perlin2D.HEIGHT / screenHeight * y));
    }

    // Linearly interpolate between a0 and a1. Weight w should be in [0.0, 1.0].
    // Uses 'Smootherstep' curve
    static double interpolate(double a0, double a1, double w){
        return (a1 - a0) * ((w * (w * 6.0 - 15.0) + 10.0) * w * w * w) + a0;
    }

    // Create a pseudorandom direction vector.
    static Point2D.Double randomGradient(int ix, int iy){
        long w = 8 * Integer.BYTES;
        long s = w / 2;

        long a = ix;
        long b = iy;

        // note we have to mark below as long literals
        a *= 3284157443L;
        b ^= a << s | a >>> w-s;
        b *= 1911520717L;
        a ^= b << s | b >>> w-s;
        a *= 2048419325;

        double random = a * (3.14159265 / ~(~0 >>> 1));

        return new Point2D.Double(sin(random),  cos(random));
    }

    // Compute dot product of distance and gradient vectors.
    static double dotGridGradient(int ix, int iy, double x, double y){

        Point2D.Double gradient = randomGradient(ix, iy);

        // get distance vector
        double dx = x - (double)ix;
        double dy = y - (double)iy;

        // dot product
        return (dx * gradient.getX() + dy * gradient.getY());
    }

    // Compute the Perlin noise value (converted to an RGB-encoded int) for the specified coordinates.
    static int perlinColor(double x, double y){
        // setup coords
        int x0 = (int)x;
        int x1 = x0 + 1;
        int y0 = (int)y;
        int y1 = y0 + 1;

        // get interpolation weights
        double sx = x - (float)x0;
        double sy = y - (float)y0;

        // interpolate between grid point gradients
        double n0, n1, ix0, ix1, value;

        n0 = dotGridGradient(x0, y0, x, y);
        n1 = dotGridGradient(x1, y0, x, y);
        ix0 = interpolate(n0, n1, sx);

        n0 = dotGridGradient(x0, y1, x, y);
        n1 = dotGridGradient(x1, y1, x, y);
        ix1 = interpolate(n0, n1, sx);

        value = interpolate(ix0, ix1, sy);

        return Color.getHSBColor(0.0f, 0.0f, (float)abs(value)).getRGB();
    }

    public static void computeSavePerlinColor(int col, int row){
        final Point2D.Double cartesianPoint = convertScreenToCartesian(col, row, Perlin2D.SIZE, Perlin2D.SIZE);
        Perlin2D.BUFFER[row * Perlin2D.SIZE + col] = perlinColor(cartesianPoint.getX(), cartesianPoint.getY());
    }

    public static void runCompleteDraw(){
        for (int row = 0; row < Perlin2D.SIZE; row ++)
        {
            for (int column = 0; column < Perlin2D.SIZE; column++)
            {
                computeSavePerlinColor(column, row);
            }
        }
    }
}