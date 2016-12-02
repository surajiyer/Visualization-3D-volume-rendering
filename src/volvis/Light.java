/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package volvis;

import util.VectorMath;

/**
 *
 * @author siyer
 */
public class Light {
    
    // color of the light
    public TFColor ambient;
    public TFColor diffuse;
    
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
    
    public TFColor getColor(double[] L, double[] N) {
        // compute halfway vector
        double[] H = VectorMath.add(L, L);
        VectorMath.normalize(H);
        
        // compute L.N and (N.H)^{\alpha}
        double l_n = VectorMath.dotproduct(L, N);
        l_n = l_n < 0 ? 0 : l_n;
        double n_h = VectorMath.dotproduct(N, H);
        n_h = (n_h < 0 || l_n < 0) ? 0 : Math.pow(n_h, alpha);
        
        // compute color
        TFColor color = new TFColor();
        color.r = k_amb*ambient.r + k_dif*diffuse.r*l_n + k_spec*n_h;
        color.g = k_amb*ambient.g + k_dif*diffuse.g*l_n + k_spec*n_h;
        color.b = k_amb*ambient.b + k_dif*diffuse.b*l_n + k_spec*n_h;
        color.a = diffuse.a;
        return color;
    }
}
