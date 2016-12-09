/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volume;

/**
 *
 * @author michel
 */
public class GradientVolume {

    public GradientVolume(Volume vol) {
        volume = vol;
        dimX = vol.getDimX();
        dimY = vol.getDimY();
        dimZ = vol.getDimZ();
        data = new VoxelGradient[dimX * dimY * dimZ];
        compute();
        maxmag = -1.0;
    }

    public VoxelGradient getGradient(int x, int y, int z) {
        return data[x + dimX * (y + dimY * z)];
    }

    
    public void setGradient(int x, int y, int z, VoxelGradient value) {
        data[x + dimX * (y + dimY * z)] = value;
    }

    public void setVoxel(int i, VoxelGradient value) {
        data[i] = value;
    }

    public VoxelGradient getVoxel(int i) {
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

    private void compute() {

        // this just initializes all gradients to the vector (0,0,0)
        for(int i = 0; i < data.length; i++){
            data[i] = zero;
        }
        for (int i=1; i< volume.getDimX()-1; i++) {
            for (int j=1; j < volume.getDimY()-1; j++) {
                for (int k=1; k < volume.getDimZ()-1; k++) {
                    float gx = (volume.getVoxel(i-1, j, k) - volume.getVoxel(i+1, j, k))/2;
                    float gy = (volume.getVoxel(i, j-1, k) - volume.getVoxel(i, j+1, k))/2;
                    float gz = (volume.getVoxel(i, j, k-1) - volume.getVoxel(i, j, k+1))/2;
                    setGradient(i, j, k, new VoxelGradient(gx, gy, gz));
                }
            }
        }

    }
    
    public double getMaxGradientMagnitude() {
        if (maxmag >= 0) {
            return maxmag;
        } else {
            double magnitude = data[0].mag;
            for (int i=0; i<data.length; i++) {
                magnitude = data[i].mag > magnitude ? data[i].mag : magnitude;
            }   
            maxmag = magnitude;
            return magnitude;
        }
    }
    
    private int dimX, dimY, dimZ;
    private VoxelGradient zero = new VoxelGradient();
    VoxelGradient[] data;
    Volume volume;
    double maxmag;
}
