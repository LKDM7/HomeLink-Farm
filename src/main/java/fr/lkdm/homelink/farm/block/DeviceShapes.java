package fr.lkdm.homelink.farm.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Outline shapes of the horizontal-facing devices, built facing north and rotated like their models. */
final class DeviceShapes {
    private DeviceShapes() {
    }

    /** One shape per horizontal facing, from boxes given in model pixels ({x1, y1, z1, x2, y2, z2}) facing north. */
    static Map<Direction, VoxelShape> horizontal(double[]... boxes) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : boxes) shape = Shapes.or(shape, rotated(box, facing));
            shapes.put(facing, shape.optimize());
        }
        return shapes;
    }

    /** Same rotation as the blockstate "y" angle: north 0, east 90, south 180, west 270 (clockwise from above). */
    private static VoxelShape rotated(double[] box, Direction facing) {
        double x1 = box[0], z1 = box[2], x2 = box[3], z2 = box[5];
        return switch (facing) {
            case EAST -> box(16 - z2, box[1], x1, 16 - z1, box[4], x2);
            case SOUTH -> box(16 - x2, box[1], 16 - z2, 16 - x1, box[4], 16 - z1);
            case WEST -> box(z1, box[1], 16 - x2, z2, box[4], 16 - x1);
            default -> box(x1, box[1], z1, x2, box[4], z2);
        };
    }

    private static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }
}
