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

import util.TFChangeListener;
import util.VectorMath;
import volume.GradientVolume;
import volume.Volume;
import volume.VoxelGradient;

/**
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
    private String type = "slicer";
    private boolean useTriLinearInterpolation = false;
    private boolean useShading = false;
    double scale;
    private Light light_0;

    public RaycastRenderer() {
        panel = new RaycastRendererPanel(this);
        panel.setSpeedLabel("0");

        // create a light model
        light_0 = new Light(0.1, 0.7, 0.2, 10);
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
        lowRes = new BufferedImage(imageSize / 8, imageSize / 8, BufferedImage.TYPE_INT_ARGB);

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

    public void setUseTriLinearInterpolation(boolean use) {
        this.useTriLinearInterpolation = use;
        this.changed();
    }

    public void setUseShading(boolean use) {
        this.useShading = use;
        this.changed();
    }

    private double getImageScale() {
        return normal.getWidth() / ((image == normal ? 1.0 : 2.0) * image.getWidth());
    }

    private short getVoxel(double[] coord) {
        if (coord[0] < 0 || coord[0] >= volume.getDimX() - 1
                || coord[1] < 0 || coord[1] >= volume.getDimY() - 1
                || coord[2] < 0 || coord[2] >= volume.getDimZ() - 1) {
            return 0;
        }

        if (useTriLinearInterpolation) {
            int x = (int) Math.floor(coord[0]);
            int y = (int) Math.floor(coord[1]);
            int z = (int) Math.floor(coord[2]);
            return volume.getVoxel(x, y, z);
        } else {
            int x1 = (int) Math.floor(coord[0]);
            int y1 = (int) Math.floor(coord[1]);
            int z1 = (int) Math.floor(coord[2]);
            int x2 = x1 + 1;
            int y2 = y1 + 1;
            int z2 = z1 + 1;

            double alpha = coord[0] - x1;
            double beta = coord[1] - y1;
            double gamma = coord[2] - z1;

            return (short) ((1 - alpha) * (1 - beta) * (1 - gamma) * volume.getVoxel(x1, y1, z1) +
                    alpha * (1 - beta) * (1 - gamma) * volume.getVoxel(x2, y1, z1) +
                    (1 - alpha) * beta * (1 - gamma) * volume.getVoxel(x1, y2, z1) +
                    alpha * beta * (1 - gamma) * volume.getVoxel(x2, y2, z1) +
                    (1 - alpha) * (1 - beta) * gamma * volume.getVoxel(x1, y1, z2) +
                    alpha * (1 - beta) * gamma * volume.getVoxel(x2, y1, z2) +
                    (1 - alpha) * beta * gamma * volume.getVoxel(x1, y2, z2) +
                    alpha * beta * gamma * volume.getVoxel(x2, y2, z2));
        }
    }

    private void slicer(double[] viewMatrix) {
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

        // sample on a plane through the origin of the volume data
        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        double max = volume.getMaximum();
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

                // map the intensity to a grey value by linear scaling
                voxelColor.r = val / max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque

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

    private void mip(double[] viewMatrix) {
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

        // sample on a plane through the origin of the volume data
        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();
        double diagLen = volume.getDiagonalLength();

        // ray starting coordinates
        double[] rayCoord = new double[3];

        // ray-volume intersection points
        double[] nearP = new double[3];
        double[] farP = new double[3];

        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                // Create a ray
                rayCoord[0] = uVec[0] * (i - imageCenter) * scale
                        + vVec[0] * (j - imageCenter) * scale
                        - viewVec[0] * diagLen + volumeCenter[0];
                rayCoord[1] = uVec[1] * (i - imageCenter) * scale
                        + vVec[1] * (j - imageCenter) * scale
                        - viewVec[1] * diagLen + volumeCenter[1];
                rayCoord[2] = uVec[2] * (i - imageCenter) * scale
                        + vVec[2] * (j - imageCenter) * scale
                        - viewVec[2] * diagLen + volumeCenter[2];

                boolean isIntersection = volume.checkIntersection(viewVec, rayCoord, nearP, farP);
                if (!isIntersection) continue;

                double[] v = VectorMath.subtract(farP, nearP);
                int maxVal = 0;
                for (double t = 0.0; t <= 1.0; t += 0.01) {
                    pixelCoord[0] = nearP[0] + v[0] * t;
                    pixelCoord[1] = nearP[1] + v[1] * t;
                    pixelCoord[2] = nearP[2] + v[2] * t;

                    int val = getVoxel(pixelCoord);
                    maxVal = maxVal > val ? maxVal : val;
                }

//                for (double t = 0.5*diagLen; t >= -0.5*diagLen; t--) {
//                    pixelCoord[0] = uVec[0] * (i - imageCenter) * scale
//                            + vVec[0] * (j - imageCenter) * scale
//                            + viewVec[0] * t
//                            + volumeCenter[0];
//                    pixelCoord[1] = uVec[1] * (i - imageCenter) * scale
//                            + vVec[1] * (j - imageCenter) * scale
//                            + viewVec[1] * t
//                            + volumeCenter[1];
//                    pixelCoord[2] = uVec[2] * (i - imageCenter) * scale
//                            + vVec[2] * (j - imageCenter) * scale
//                            + viewVec[2] * t
//                            + volumeCenter[2];
//                    
//                    int val = getVoxel(pixelCoord);
//                    maxVal = maxVal > val ? maxVal : val;
//                }

                // map the intensity to a grey value by linear scaling
                voxelColor.r = maxVal / max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = maxVal > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque

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

    private TFColor getShadedColor(double[] coord, TFColor voxelColor, double[] viewVec) {
        if (coord[0] < 0 || coord[0] >= volume.getDimX()
                || coord[1] < 0 || coord[1] >= volume.getDimY()
                || coord[2] < 0 || coord[2] >= volume.getDimZ()) {
            return new TFColor(0, 0, 0, 1);
        }

        VoxelGradient vg = gradients.getGradient(
                (int) Math.floor(coord[0]),
                (int) Math.floor(coord[1]),
                (int) Math.floor(coord[2]));
        light_0.setDiffuse(voxelColor);
        return light_0.getColor(viewVec, vg.getNormal(), viewVec);
    }

    private void composite(double[] viewMatrix) {
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
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        TFColor voxelColor, voxelColor1 = new TFColor();
        double diagLen = volume.getDiagonalLength();

        // ray starting coordinates
        double[] rayCoord = new double[3];

        // ray-volume intersection points
        double[] nearP = new double[3];
        double[] farP = new double[3];

        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                // Create a ray
                rayCoord[0] = uVec[0] * (i - imageCenter) * scale
                        + vVec[0] * (j - imageCenter) * scale
                        - viewVec[0] * diagLen + volumeCenter[0];
                rayCoord[1] = uVec[1] * (i - imageCenter) * scale
                        + vVec[1] * (j - imageCenter) * scale
                        - viewVec[1] * diagLen + volumeCenter[1];
                rayCoord[2] = uVec[2] * (i - imageCenter) * scale
                        + vVec[2] * (j - imageCenter) * scale
                        - viewVec[2] * diagLen + volumeCenter[2];

                // Check intersection points with the ray
                boolean isIntersection = volume.checkIntersection(viewVec, rayCoord, nearP, farP);
                if (!isIntersection) continue;

                voxelColor1.set(0, 0, 0, 1);
                double[] v = VectorMath.subtract(farP, nearP);
                for (double t = 0.0; t <= 1.0; t += 0.01) {
                    pixelCoord[0] = nearP[0] + v[0] * t;
                    pixelCoord[1] = nearP[1] + v[1] * t;
                    pixelCoord[2] = nearP[2] + v[2] * t;
                    int val = getVoxel(pixelCoord);

                    // apply the transfer function to obtain a color
                    voxelColor = tFunc.getColor(val);

                    // phong shading
                    if (useShading) {
                        voxelColor = getShadedColor(pixelCoord, voxelColor, viewVec);
                    }

                    // compositing
                    voxelColor1.r = voxelColor.a * voxelColor.r + (1 - voxelColor.a) * voxelColor1.r;
                    voxelColor1.g = voxelColor.a * voxelColor.g + (1 - voxelColor.a) * voxelColor1.g;
                    voxelColor1.b = voxelColor.a * voxelColor.b + (1 - voxelColor.a) * voxelColor1.b;
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

    private double getLevoyOpacity(double gradient, int voxelValue) {
        TransferFunction2DEditor.TriangleWidget tw = this.tfEditor2D.triangleWidget;

        if (gradient > tw.maxGradient || gradient < tw.minGradient)
            return 0;
        else if (gradient == 0 && voxelValue == tw.baseIntensity) {
            return tw.color.a;
        } else if (gradient > 0 &&
                (voxelValue - tw.radius * gradient) <= tw.baseIntensity &&
                tw.baseIntensity <= (voxelValue + tw.radius * gradient))
            return tw.color.a * (1 - Math.abs((tw.baseIntensity - voxelValue) / gradient) / tw.radius);
        else
            return 0;
    }

    private double getGradient(double[] coord) {
        if (coord[0] < 0 || coord[0] >= volume.getDimX() - 1
                || coord[1] < 0 || coord[1] >= volume.getDimY() - 1
                || coord[2] < 0 || coord[2] >= volume.getDimZ() - 1) {
            return 0;
        }

        if (useTriLinearInterpolation) {
            int x = (int) Math.floor(coord[0]);
            int y = (int) Math.floor(coord[1]);
            int z = (int) Math.floor(coord[2]);
            return gradients.getGradient(x, y, z).mag;
        } else {
            int x1 = (int) Math.floor(coord[0]);
            int y1 = (int) Math.floor(coord[1]);
            int z1 = (int) Math.floor(coord[2]);
            int x2 = x1 + 1;
            int y2 = y1 + 1;
            int z2 = z1 + 1;

            double alpha = coord[0] - x1;
            double beta = coord[1] - y1;
            double gamma = coord[2] - z1;

            return (short) ((1 - alpha) * (1 - beta) * (1 - gamma) * gradients.getGradient(x1, y1, z1).mag +
                    alpha * (1 - beta) * (1 - gamma) * gradients.getGradient(x2, y1, z1).mag +
                    (1 - alpha) * beta * (1 - gamma) * gradients.getGradient(x1, y2, z1).mag +
                    alpha * beta * (1 - gamma) * gradients.getGradient(x2, y2, z1).mag +
                    (1 - alpha) * (1 - beta) * gamma * gradients.getGradient(x1, y1, z2).mag +
                    alpha * (1 - beta) * gamma * gradients.getGradient(x2, y1, z2).mag +
                    (1 - alpha) * beta * gamma * gradients.getGradient(x1, y2, z2).mag +
                    alpha * beta * gamma * gradients.getGradient(x2, y2, z2).mag);
        }
    }

    private void transfer2D(double[] viewMatrix) {
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
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        TFColor voxelColor, finalColor = new TFColor();
        double diagLen = volume.getDiagonalLength();

        // ray starting coordinates
        double[] rayCoord = new double[3];

        // ray-volume intersection points
        double[] nearP = new double[3];
        double[] farP = new double[3];

        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                // Create a ray
                rayCoord[0] = uVec[0] * (i - imageCenter) * scale
                        + vVec[0] * (j - imageCenter) * scale
                        - viewVec[0] * diagLen + volumeCenter[0];
                rayCoord[1] = uVec[1] * (i - imageCenter) * scale
                        + vVec[1] * (j - imageCenter) * scale
                        - viewVec[1] * diagLen + volumeCenter[1];
                rayCoord[2] = uVec[2] * (i - imageCenter) * scale
                        + vVec[2] * (j - imageCenter) * scale
                        - viewVec[2] * diagLen + volumeCenter[2];

                // Check intersection points with the ray
                boolean isIntersection = volume.checkIntersection(viewVec, rayCoord, nearP, farP);
                if (!isIntersection) continue;

                finalColor.set(0, 0, 0, 1);
                double[] v = VectorMath.subtract(farP, nearP);
                for (double t = 0.0; t <= 1.0; t += 0.01) {
                    pixelCoord[0] = nearP[0] + v[0] * t;
                    pixelCoord[1] = nearP[1] + v[1] * t;
                    pixelCoord[2] = nearP[2] + v[2] * t;
                    int val = getVoxel(pixelCoord);

                    // apply the transfer function to obtain a color
                    voxelColor = new TFColor(this.tfEditor2D.triangleWidget.color);
                    voxelColor.a = this.getLevoyOpacity(this.getGradient(pixelCoord), val);

                    // phong shading
                    if (useShading) {
                        voxelColor = getShadedColor(pixelCoord, voxelColor, viewVec);
                    }

                    // compositing
                    finalColor.r = voxelColor.a * voxelColor.r + (1 - voxelColor.a) * finalColor.r;
                    finalColor.g = voxelColor.a * voxelColor.g + (1 - voxelColor.a) * finalColor.g;
                    finalColor.b = voxelColor.a * voxelColor.b + (1 - voxelColor.a) * finalColor.b;
                }

                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = finalColor.a <= 1.0 ? (int) Math.floor(finalColor.a * 255) : 255;
                int c_red = finalColor.r <= 1.0 ? (int) Math.floor(finalColor.r * 255) : 255;
                int c_green = finalColor.g <= 1.0 ? (int) Math.floor(finalColor.g * 255) : 255;
                int c_blue = finalColor.b <= 1.0 ? (int) Math.floor(finalColor.b * 255) : 255;
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

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

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
        scale = this.getImageScale();

        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        long startTime = System.currentTimeMillis();
        switch (type) {
            case "slicer":
                slicer(viewMatrix);
                break;
            case "mip":
                mip(viewMatrix);
                break;
            case "composite":
                composite(viewMatrix);
                break;
            case "transfer2D":
                transfer2D(viewMatrix);
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
        double halfWidth = (image.getWidth() / 2.0) * scale;
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
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).changed();
        }
    }
}
