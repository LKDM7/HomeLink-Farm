package fr.lkdm.homelink.farm.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;

/** Small depth-tested field survey marks, shared by selection, irrigation and diagnostics. */
public final class FieldGuide {
    public static final int WATER = 0x65CFD5;
    public static final int GOLD = 0xD2B181;
    public static final int FAULT = 0xEA8770;
    public static final int IDLE = 0xA2ADA8;

    private FieldGuide() { }

    public static void line(PoseStack pose, VertexConsumer out, double x, double y, double z,
                            double endX, double endY, double endZ, int color, float alpha) {
        double length = Math.sqrt((endX-x)*(endX-x) + (endY-y)*(endY-y) + (endZ-z)*(endZ-z));
        if (length < 0.00001 || alpha <= 0) return;
        float nx = (float)((endX-x)/length), ny = (float)((endY-y)/length), nz = (float)((endZ-z)/length);
        int a = (int)(255 * Math.clamp(alpha, 0, 1));
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        out.addVertex(pose.last().pose(), (float)x, (float)y, (float)z).setColor(r,g,b,a).setNormal(pose.last(),nx,ny,nz);
        out.addVertex(pose.last().pose(), (float)endX, (float)endY, (float)endZ).setColor(r,g,b,a).setNormal(pose.last(),nx,ny,nz);
    }

    public static void ring(PoseStack pose, VertexConsumer out, double x, double y, double z,
                            double radius, int color, float alpha) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12, b = (i + 1) * Math.PI / 12;
            line(pose,out,x+Math.cos(a)*radius,y,z+Math.sin(a)*radius,
                    x+Math.cos(b)*radius,y,z+Math.sin(b)*radius,color,alpha);
        }
    }

    /** Short L-shaped corners preserve exact bounds without enclosing the crop in a wire cage. */
    public static void corners(PoseStack pose, VertexConsumer out, AABB box, int color, float alpha) {
        double sx = Math.min(0.65, box.getXsize()/3), sy = Math.min(0.8, box.getYsize()/3), sz = Math.min(0.65,box.getZsize()/3);
        for (int ix = 0; ix < 2; ix++) for (int iy = 0; iy < 2; iy++) for (int iz = 0; iz < 2; iz++) {
            double x = ix == 0 ? box.minX : box.maxX, y = iy == 0 ? box.minY : box.maxY, z = iz == 0 ? box.minZ : box.maxZ;
            line(pose,out,x,y,z,x+(ix==0?sx:-sx),y,z,color,alpha);
            line(pose,out,x,y,z,x,y+(iy==0?sy:-sy),z,color,alpha);
            line(pose,out,x,y,z,x,y,z+(iz==0?sz:-sz),color,alpha);
        }
    }

    public static void zone(PoseStack pose, VertexConsumer out, AABB box, int color, float alpha, double time) {
        corners(pose,out,box,color,alpha);
        // Sparse survey marks cap work even for a very large connector selection.
        int steps = Math.min(64, Math.max(1,(int)Math.ceil(Math.max(box.getXsize(),box.getZsize()))));
        for (int i = 0; i < steps; i++) {
            double t = i/(double)steps, end = (i+0.42)/steps;
            float glow = alpha * (0.28F + 0.18F * (float)(0.5 + 0.5*Math.sin(time*0.05-i*0.35)));
            for (double y : new double[] {box.minY,box.maxY}) {
                double x0=box.minX+t*box.getXsize(), x1=box.minX+end*box.getXsize();
                double z0=box.minZ+t*box.getZsize(), z1=box.minZ+end*box.getZsize();
                line(pose,out,x0,y,box.minZ,x1,y,box.minZ,color,glow);
                line(pose,out,x0,y,box.maxZ,x1,y,box.maxZ,color,glow);
                line(pose,out,box.minX,y,z0,box.minX,y,z1,color,glow);
                line(pose,out,box.maxX,y,z0,box.maxX,y,z1,color,glow);
            }
        }
    }

    public static float distanceFade(double squaredDistance, double maximum) {
        return (float)Math.clamp((maximum-Math.sqrt(squaredDistance))/(maximum*0.3),0,1);
    }
}
