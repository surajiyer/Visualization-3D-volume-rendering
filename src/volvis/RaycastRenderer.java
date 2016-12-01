/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volvis;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import gui.RaycastRendererPanel;
import gui.TransferFunction2DEditor;
import gui.TransferFunctionEditor;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import util.TFChangeListener;
import util.VectorMath;
import volume.GradientVolume;
import volume.Volume;

/**
 *
 * @author michel
 */
public class RaycastRenderer extends Renderer implements TFChangeListener {

    private Volume volume = null;
    private GradientVolume gradients = null;
    RaycastRendererPanel panel;
    TransferFunction tFunc;
    TransferFunctionEditor tfEditor;
    TransferFunction2DEditor tfEditor2D;
    private BufferedImage image;
    private BufferedImage normal;
    private BufferedImage lowRes;
    private double[] viewMatrix = new double[4 * 4];
    String type = "slicer";
    boolean useTriLinearInterpolation = false;
    boolean useColor = false;
    
    public RaycastRenderer() {
        panel = new RaycastRendererPanel(this);
        panel.setSpeedLabel("0");
    }

    public void setVolume(Volume vol) {
        System.out.println("Assigning volume");
        volume = vol;

        System.out.println("Computing gradients");
        gradients = new GradientVolume(vol);

        // set up image for storing the resulting rendering
        // the image width and height are equal to the length of the volume diagonal
        int imageSize = (int) Math.floor(vol.getDiagonalLength());
        if (imageSize % 2 != 0) {
            imageSize = imageSize + 1;
        }
        normal = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        lowRes = new BufferedImage(imageSize/8, imageSize/8, BufferedImage.TYPE_INT_ARGB);
        
        // create a standard TF where lowest intensity maps to black, the highest to white, and opacity increases
        // linearly from 0.0 to 1.0 over the intensity range
        tFunc = new TransferFunction(volume.getMinimum(), volume.getMaximum());
        
        // uncomment this to initialize the TF with good starting values for the orange dataset 
        tFunc.setTestFunc();
        
        tFunc.addTFChangeListener(this);
        tfEditor = new TransferFunctionEditor(tFunc, volume.getHistogram());
        
        tfEditor2D = new TransferFunction2DEditor(volume, gradients);
        tfEditor2D.addTFChangeListener(this);

        System.out.println("Finished initialization of RaycastRenderer");
    }

    public RaycastRendererPanel getPanel() {
        return panel;
    }

    public TransferFunction2DEditor getTF2DPanel() {
        return tfEditor2D;
    }
    
    public TransferFunctionEditor getTFPanel() {
        return tfEditor;
    }
    
    public void setType(String type) {
        this.type = type;
        this.changed();
    }
    
    public void useTriLinearInterpolation(boolean use) {
        this.useTriLinearInterpolation = use;
        this.changed();
    }
    
    public void useColor(boolean use) {
        this.useColor = use;
        this.changed();
    }
    
    public double getImageScale() {
        return normal.getWidth() / ((image == normal ? 1.0 : 2.0) * image.getWidth());
    }
    

    short getVoxel(double[] coord) {
        if (coord[0] < 0 || coord[0] >= volume.getDimX()-1 
                || coord[1] < 0 || coord[1] >= volume.getDimY()-1
                || coord[2] < 0 || coord[2] >= volume.getDimZ()-1) {
            return 0;
        }

        if(useTriLinearInterpolation) {
            int x = (int) Math.floor(coord[0]);
            int y = (int) Math.floor(coord[1]);
            int z = (int) Math.floor(coord[2]);
            return volume.getVoxel(x, y, z);
        } else {
            int x1 = (int) Math.floor(coord[0]);
            int y1 = (int) Math.floor(coord[1]);
            int z1 = (int) Math.floor(coord[2]);
            int x2 = x1+1;//(int) Math.ceil(coord[0]);
            int y2 = y1+1;//(int) Math.ceil(coord[1]);
            int z2 = z1+1;//(int) Math.ceil(coord[2]);

            double alpha = coord[0] - x1;
            double beta = coord[1] - y1;
            double gamma = coord[2] - z1;

            return (short) ((1-alpha)*(1-beta)*(1-gamma)*volume.getVoxel(x1, y1, z1)+
                    alpha*(1-beta)*(1-gamma)*volume.getVoxel(x2, y1, z1)+
                    (1-alpha)*beta*(1-gamma)*volume.getVoxel(x1, y2, z1)+
                    alpha*beta*(1-gamma)*volume.getVoxel(x2, y2, z1)+
                    (1-alpha)*(1-beta)*gamma*volume.getVoxel(x1, y1, z2)+
                    alpha*(1-beta)*gamma*volume.getVoxel(x2, y1, z2)+
                    (1-alpha)*beta*gamma*volume.getVoxel(x1, y2, z2)+
                    alpha*beta*gamma*volume.getVoxel(x2, y2, z2));
        }
    }
    

    void slicer(double[] viewMatrix) {
        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        double[] viewVec = new double[3];
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2 , volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        double scale = this.getImageScale();
        TFColor voxelColor = new TFColor();
        
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                pixelCoord[0] = uVec[0] * (i - imageCenter) * scale
                        + vVec[0] * (j - imageCenter) * scale
                        + volumeCenter[0];
                pixelCoord[1] = uVec[1] * (i - imageCenter) * scale
                        + vVec[1] * (j - imageCenter) * scale
                        + volumeCenter[1];
                pixelCoord[2] = uVec[2] * (i - imageCenter) * scale
                        + vVec[2] * (j - imageCenter) * scale
                        + volumeCenter[2];

                int val = getVoxel(pixelCoord);
                
                // Generate pixel color
                if(useColor) {
                    // Apply the transfer function to obtain a color
                    voxelColor = tFunc.getColor(val);
                } else {
                    // Alternatively, map the intensity to a grey value by linear scaling
                    voxelColor.r = val/max;
                    voxelColor.g = voxelColor.r;
                    voxelColor.b = voxelColor.r;
                    voxelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                }
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }
    }
    
    void mip(double[] viewMatrix) {
        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        double[] viewVec = new double[3];
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2 , volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        double scale = this.getImageScale();
        TFColor voxelColor = new TFColor();
        double range = volume.getDiagonalLength();
        //double range = Math.max(Math.max(volume.getDimX(), volume.getDimY()), volume.getDimZ());
        
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                int maxVal = 0;
                for (double t = 0.5*range; t >= -0.5*range; t--) {
                    pixelCoord[0] = uVec[0] * (i - imageCenter) * scale
                            + vVec[0] * (j - imageCenter) * scale
                            + viewVec[0] * t
                            + volumeCenter[0];
                    pixelCoord[1] = uVec[1] * (i - imageCenter) * scale
                            + vVec[1] * (j - imageCenter) * scale
                            + viewVec[1] * t
                            + volumeCenter[1];
                    pixelCoord[2] = uVec[2] * (i - imageCenter) * scale
                            + vVec[2] * (j - imageCenter) * scale
                            + viewVec[2] * t
                            + volumeCenter[2];
                    
                    int val = getVoxel(pixelCoord);
                    maxVal = maxVal > val ? maxVal : val;
                }
                
                // Generate pixel color
                if(useColor) {
                    // Apply the transfer function to obtain a color
                    voxelColor = tFunc.getColor(maxVal);
                } else {
                    // Alternatively, map the intensity to a grey value by linear scaling
                    voxelColor.r = maxVal/max;
                    voxelColor.g = voxelColor.r;
                    voxelColor.b = voxelColor.r;
                    voxelColor.a = maxVal > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                }
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }
    }
    
    
    void composite(double[] viewMatrix) {
        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        double[] viewVec = new double[3];
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.normalize(viewVec);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2 , volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        double scale = this.getImageScale();
        TFColor voxelColor1 = new TFColor();
        TFColor voxelColor2 = new TFColor();
        double range = volume.getDiagonalLength();
        double[] rayCoord = new double[3];
        double[] P = new double[2];
        double[] nearP = new double[3];
        double[] farP = new double[3];
        
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                // Create a ray
                rayCoord[0] = uVec[0] * (i - imageCenter) * scale
                        + vVec[0] * (j - imageCenter) * scale
                        - viewVec[0]*range + volumeCenter[0];
                rayCoord[1] = uVec[1] * (i - imageCenter) * scale
                        + vVec[1] * (j - imageCenter) * scale
                        - viewVec[1]*range + volumeCenter[1];
                rayCoord[2] = uVec[2] * (i - imageCenter) * scale
                        + vVec[2] * (j - imageCenter) * scale
                        - viewVec[2]*range + volumeCenter[2];
                
                // Check intersection points with the ray
                boolean isIntersection = volume.checkIntersection(viewVec, rayCoord, nearP, farP);
                if(!isIntersection) continue;
//                double tmin = P[0]; double tmax = P[1];
                double[] v = VectorMath.subtract(nearP, farP);
                
                int maxVal = 0;
                voxelColor1.set(0,0,0,0);
                for(double t = 0.0; t <= 1.0; t+=0.01) {
                    pixelCoord[0] = farP[0] + v[0] * t;
                    pixelCoord[1] = farP[1] + v[1] * t;
                    pixelCoord[2] = farP[2] + v[2] * t;
                    
                    int val = getVoxel(pixelCoord);
                    maxVal = val;//maxVal > val ? maxVal : val;
                    
                    // Generate pixel color
                    if(useColor) {
                        // Apply the transfer function to obtain a color
                        voxelColor2 = tFunc.getColor(maxVal);
                    } else {
                        // Alternatively, map the intensity to a grey value by linear scaling
                        voxelColor2.r = maxVal/max;
                        voxelColor2.g = voxelColor2.r;
                        voxelColor2.b = voxelColor2.r;
                        voxelColor2.a = maxVal > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                    }

                    // Compositing
                    voxelColor2.r = voxelColor2.a*voxelColor2.r + (1-voxelColor2.a)*voxelColor1.r;
                    voxelColor2.g = voxelColor2.a*voxelColor2.g + (1-voxelColor2.a)*voxelColor1.g;
                    voxelColor2.b = voxelColor2.a*voxelColor2.b + (1-voxelColor2.a)*voxelColor1.b;
//                    voxelColor2.a = (voxelColor2.r + voxelColor2.g + voxelColor2.b) > 0 ? 1.0 : 0.0;
//                    voxelColor2.a = (1-voxelColor2.a)*voxelColor1.a;
                    voxelColor1.set(voxelColor2);
                }
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor1.a <= 1.0 ? (int) Math.floor(voxelColor1.a * 255) : 255;
                int c_red = voxelColor1.r <= 1.0 ? (int) Math.floor(voxelColor1.r * 255) : 255;
                int c_green = voxelColor1.g <= 1.0 ? (int) Math.floor(voxelColor1.g * 255) : 255;
                int c_blue = voxelColor1.b <= 1.0 ? (int) Math.floor(voxelColor1.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }
    }


    private void drawBoundingBox(GL2 gl) {
        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);
        gl.glLineWidth(1.5f);
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

//        gl.glBegin(GL.GL_LINE_LOOP);
//        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
//        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
//        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
//        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
//        gl.glEnd();

//        gl.glBegin(GL.GL_LINE_LOOP);
//        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
//        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
//        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
//        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
//        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glPopAttrib();
    }

    @Override
    public void visualize(GL2 gl) {
        if (volume == null) {
            return;
        }

        drawBoundingBox(gl);

        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, viewMatrix, 0);
        
        // set image
        image = interactiveMode ? lowRes : normal;
        
        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        long startTime = System.currentTimeMillis();
        switch(type) {
            case "slicer":
                slicer(viewMatrix);
                break;
            case "mip":
                mip(viewMatrix);
                break;
            case "composite":
                composite(viewMatrix);
                break;
            default:
                slicer(viewMatrix);
        }
        
        long endTime = System.currentTimeMillis();
        double runningTime = (endTime - startTime);
        panel.setSpeedLabel(Double.toString(runningTime));

        Texture texture = AWTTextureIO.newTexture(gl.getGLProfile(), image, false);

        gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        // draw rendered image as a billboard texture
        texture.enable(gl);
        texture.bind(gl);
        double halfWidth = (image.getWidth() / 2.0)*(normal.getWidth()/((image==normal?1.0:2.0)*image.getWidth()));
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl.glTexCoord2d(0.0, 0.0);
        gl.glVertex3d(-halfWidth, -halfWidth, 0.0);
        gl.glTexCoord2d(0.0, 1.0);
        gl.glVertex3d(-halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 1.0);
        gl.glVertex3d(halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 0.0);
        gl.glVertex3d(halfWidth, -halfWidth, 0.0);
        gl.glEnd();
        texture.disable(gl);
        texture.destroy(gl);
        gl.glPopMatrix();

        gl.glPopAttrib();

        if (gl.glGetError() > 0) {
            System.out.println("some OpenGL error: " + gl.glGetError());
        }
    }

    @Override
    public void changed() {
        for (int i=0; i < listeners.size(); i++) {
            listeners.get(i).changed();
        }
    }
}
