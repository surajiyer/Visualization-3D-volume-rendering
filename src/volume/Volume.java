/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volume;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import util.VectorMath;

/**
 *
 * @author michel
 */
public class Volume {
    
    public Volume(int xd, int yd, int zd) {
        data = new short[xd*yd*zd];
        dimX = xd;
        dimY = yd;
        dimZ = zd;
    }
    
    public Volume(File file) {
        
        try {
            VolumeIO reader = new VolumeIO(file);
            dimX = reader.getXDim();
            dimY = reader.getYDim();
            dimZ = reader.getZDim();
            data = reader.getData().clone();
            computeHistogram();
        } catch (IOException ex) {
            System.out.println("IO exception");
        }
    }
    
    
    public short getVoxel(int x, int y, int z) {
        return data[x + dimX*(y + dimY * z)];
    }
    
    public void setVoxel(int x, int y, int z, short value) {
        data[x + dimX*(y + dimY*z)] = value;
    }

    public void setVoxel(int i, short value) {
        data[i] = value;
    }
    
    public short getVoxel(int i) {
        return data[i];
    }
    
    public int getDimX() {
        return dimX;
    }
    
    public int getDimY() {
        return dimY;
    }
    
    public int getDimZ() {
        return dimZ;
    }
    
    public double getDiagonalLength() {
        return Math.sqrt(dimX * dimX + dimY * dimY + dimZ * dimZ);
    }

    public short getMinimum() {
        short minimum = data[0];
        for (int i=0; i<data.length; i++) {
            minimum = data[i] < minimum ? data[i] : minimum;
        }
        return minimum;
    }

    public short getMaximum() {
        short maximum = data[0];
        for (int i=0; i<data.length; i++) {
            maximum = data[i] > maximum ? data[i] : maximum;
        }
        return maximum;
    }
    
    public boolean checkIntersection(double[] rayDir, double[] rayOrg, double[] P) {
        rayDir[0] = rayDir[0] == 0 ? 0.001 : rayDir[0];
        rayDir[1] = rayDir[1] == 0 ? 0.001 : rayDir[1];
        rayDir[2] = rayDir[2] == 0 ? 0.001 : rayDir[2];
        
        double[] lb = {0d, 0d, 0d};
        double[] rt = {dimX, dimY, dimZ};
        double[] dirfrac = {1d/rayDir[0], 1d/rayDir[1], 1d/rayDir[2]};
        double t1 = (lb[0] - rayOrg[0])*dirfrac[0];
        double t2 = (rt[0] - rayOrg[0])*dirfrac[0];
        double t3 = (lb[1] - rayOrg[1])*dirfrac[1];
        double t4 = (rt[1] - rayOrg[1])*dirfrac[1];
        double t5 = (lb[2] - rayOrg[2])*dirfrac[2];
        double t6 = (rt[2] - rayOrg[2])*dirfrac[2];
        
        double tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
        double tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));
        P[0] = tmin; P[1] = tmax;
        
        // if tmax < 0, ray (line) is intersecting AABB, but whole AABB is behind us
        if (tmax < 0) {
            return false;
        }

        // if tmin > tmax, ray doesn't intersect AABB
        if (tmin > tmax) {
            return false;
        }
           
        // if tmin < 0, origin is inside or after the box
        return true;
    }
    
    public boolean checkIntersection(double[] rayDir, double[] rayOrg, double[] nearP, double[] farP) {
        double[] t = new double[2];
        boolean result = this.checkIntersection(rayDir, rayOrg, t);
        nearP[0] = rayOrg[0] + t[0]*rayDir[0]; farP[0] = rayOrg[0] + t[1]*rayDir[0];
        nearP[1] = rayOrg[1] + t[0]*rayDir[1]; farP[1] = rayOrg[1] + t[1]*rayDir[1];
        nearP[2] = rayOrg[2] + t[0]*rayDir[2]; farP[2] = rayOrg[2] + t[1]*rayDir[2];
        return result;
    }
 
    public int[] getHistogram() {
        return histogram;
    }
    
    private void computeHistogram() {
        histogram = new int[getMaximum() + 1];
        for (int i=0; i<data.length; i++) {
            histogram[data[i]]++;
        }
    }
    
    private int dimX, dimY, dimZ;
    private short[] data;
    private int[] histogram;
}
