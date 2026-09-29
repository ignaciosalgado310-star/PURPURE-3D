package com.igygaming.gokufx;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Visuals {
    private Visuals() {}

    public static Display.ItemDisplay spawnItem(ServerLevel level, Item item, Vec3 pos,
                                                Vector3f scale, Quaternionf rotation) {
        Display.ItemDisplay display = EntityType.ITEM_DISPLAY.create(level);
        if (display == null) {
            throw new IllegalStateException("No se pudo crear ItemDisplay");
        }

        display.setItemStack(new ItemStack(item));
        display.setItemTransform(ItemDisplayContext.FIXED);
        display.setNoGravity(true);
        display.setGlowingTag(true);
        display.setPos(pos.x, pos.y, pos.z);
        display.setTransformation(new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Quaternionf(rotation),
                new Vector3f(scale),
                new Quaternionf()
        ));
        level.addFreshEntity(display);
        return display;
    }

    public static void setTransform(Display.ItemDisplay display, Vec3 pos,
                                    Vector3f scale, Quaternionf rotation) {
        if (display == null || display.isRemoved()) return;
        display.setPos(pos.x, pos.y, pos.z);
        display.setTransformation(new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Quaternionf(rotation),
                new Vector3f(scale),
                new Quaternionf()
        ));
    }

    public static void remove(Display.ItemDisplay display) {
        if (display != null && !display.isRemoved()) {
            display.discard();
        }
    }

    public static Quaternionf faceZ(Vec3 direction) {
        Vector3f to = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        if (to.lengthSquared() < 0.0001F) {
            return new Quaternionf();
        }
        to.normalize();
        return new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, to.x, to.y, to.z);
    }
}
