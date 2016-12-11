/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volume;

import util.VectorMath;

/**
 *
 * @author michel
 */
public class VoxelGradient {

    public double x, y, z;
    public double mag;
    
    public VoxelGradient() {
        x = y = z = mag = 0.0;
    }
    
    public VoxelGradient(double gx, double gy, double gz) {
        x = gx;
        y = gy;
        z = gz;
        mag = Math.sqrt(x*x + y*y + z*z);
    }

    public double[] getNormal() {
        mag = (mag == 0.0) ? 0.0001 : mag;
        return new double[] {x/mag, y/mag, z/mag};
    }
}
