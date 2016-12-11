/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package volvis;

import util.VectorMath;

/**
 * @author siyer
 */
public class Light {

    // color of the light
    private TFColor ambient;
    private TFColor diffuse;

    double k_amb;
    double k_dif;
    double k_spec;
    double alpha;

    public Light(double k_amb, double k_dif, double k_spec, double alpha) {
        ambient = new TFColor();
        diffuse = new TFColor();
        this.k_amb = k_amb;
        this.k_dif = k_dif;
        this.k_spec = k_spec;
        this.alpha = alpha;
    }

    public void setAmbient(TFColor color) {
        ambient.set(color);
    }

    public void setDiffuse(TFColor color) {
        diffuse.set(color);
    }

    // Blinn-Phong shading model
    public TFColor getColorSimplified(double[] L, double[] N, double[] V) {
        // compute halfway vector
        // H = (L+V)/|L+V|
        double[] H = VectorMath.add(L, V);
        VectorMath.normalize(H);

        // compute L.N and (N.H)^{\alpha}
        double l_n = Math.max(0, VectorMath.dotproduct(L, N));
        double n_h = VectorMath.dotproduct(N, H);
        n_h = (n_h <= 0 || l_n <= 0) ? 0 : Math.pow(n_h, alpha);

        // compute color
        TFColor color = new TFColor(
                k_amb * ambient.r + k_dif * diffuse.r * l_n + k_spec * n_h,
                k_amb * ambient.g + k_dif * diffuse.g * l_n + k_spec * n_h,
                k_amb * ambient.b + k_dif * diffuse.b * l_n + k_spec * n_h,
                diffuse.a);
        return color;
    }

    // Standard Phong shading model
    public TFColor getColor(double[] L, double[] N, double[] V) {
        // compute reflection vector
        // R = 2(N.L)N - L
        double[] R = VectorMath.subtract(
                VectorMath.scale(N, 2*VectorMath.dotproduct(N, L)), L);

        // compute L.N and (V.R)^{\alpha}
        double l_n = Math.max(0, VectorMath.dotproduct(L, N));
        double v_r = VectorMath.dotproduct(V, R);
        v_r = (v_r <= 0 || l_n <= 0) ? 0 : Math.pow(v_r, alpha);

        // compute color
        TFColor color = new TFColor(
                k_amb * ambient.r + k_dif * diffuse.r * l_n + k_spec * diffuse.r * v_r,
                k_amb * ambient.g + k_dif * diffuse.g * l_n + k_spec * diffuse.g * v_r,
                k_amb * ambient.b + k_dif * diffuse.b * l_n + k_spec * diffuse.b * v_r,
                diffuse.a);
        return color;
    }
}
