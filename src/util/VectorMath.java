/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

/**
 *
 * @author michel
 */
public class VectorMath {

    // assign coefficients c0..c2 to vector v
    public static void setVector(double[] v, double c0, double c1, double c2) {
        v[0] = c0;
        v[1] = c1;
        v[2] = c2;
    }
    
    // add two vectors
    public static double[] add(double[] v, double[] w) {
        return new double[] {v[0]+w[0], v[1]+w[1], v[2]+w[2]};
    }
    
    // subtract two vectors
    public static double[] subtract(double[] v, double[] w) {
        return new double[] {v[0]-w[0], v[1]-w[1], v[2]-w[2]};
    }
    
    // scale the vector along different axises by given scales
    public static double[] scale(double[] v, double s0, double s1, double s2) {
        return new double[] {v[0]*s0, v[1]*s0, v[2]*s0};
    }
    
    // scale the vector along all axises by given scale
    public static double[] scale(double[] v, double s) {
        return VectorMath.scale(v, s, s, s);
    }

    // compute dotproduct of vectors v and w
    public static double dotproduct(double[] v, double[] w) {
        double r = 0;
        for (int i=0; i<3; i++) {
            r += v[i] * w[i];
        }
        return r;
    }

    // compute distance between vectors v and w
    public static double distance(double[] v, double[] w) {
        double[] tmp = new double[3];
        VectorMath.setVector(tmp, v[0]-w[0], v[1]-w[1], v[2]-w[2]);
        return Math.sqrt(VectorMath.dotproduct(tmp, tmp));
    }

    // compute dotproduct of v and w
    public static double[] crossproduct(double[] v, double[] w, double[] r) {
        r[0] = v[1] * w[2] - v[2] * w[1];
        r[1] = v[2] * w[0] - v[0] * w[2];
        r[2] = v[0] * w[1] - v[1] * w[0];
        return r;
    }
    
    // compute length of vector v
    public static double length(double[] v) {
        return Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
    }
    
    // compute length of vector v
    public static void normalize(double[] v) {
        double len = VectorMath.length(v);
        VectorMath.setVector(v, v[0]/len, v[1]/len, v[2]/len);
    }
}
