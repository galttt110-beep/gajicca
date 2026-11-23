package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UndergroundDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Detektuje samo ispod ovog Y nivoa.")
        .defaultValue(0)
        .sliderRange(-64, 64)
        .build());

    private final Setting<Boolean> pistons = sgGeneral.add(new BoolSetting.Builder()
        .name("pistons").description("Detektuj pistone.").defaultValue(true).build());

    private final Setting<Boolean> sounds = sgGeneral.add(new BoolSetting.Builder()
        .name("sounds").description("Detektuj zvukove (kopanje, sanduci...).").defaultValue(true).build());

    private final Setting<Boolean> chat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alert").description("Piši koordinate u chat.").defaultValue(true).build());

    private final Setting<Integer> lifetime = sgGeneral.add(new IntSetting.Builder()
        .name("lifetime-seconds").description("Koliko dugo ostaje marker.").defaultValue(30).min(1).sliderMax(120).build());

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(255, 80, 0, 80)).build());

    private final Map<BlockPos, Long> hits = new ConcurrentHashMap<>();

    public UndergroundDetector() {
        super(AddonTemplate.CATEGORY, "underground-detector", "Detektuje piston i zvuk aktivnost ispod zemlje.");
    }

    @Override
    public void onDeactivate() {
        hits.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (pistons.get() && event.packet instanceof BlockEventS2CPacket p) {
            if (p.getPos().getY() > maxY.get()) return;
            if (p.getBlock() == Blocks.PISTON || p.getBlock() == Blocks.STICKY_PISTON) {
                add(p.getPos().toImmutable(), "Piston");
            }
        }

        if (sounds.get() && event.packet instanceof PlaySoundS2CPacket s) {
            if (s.getY() > maxY.get()) return;
            add(BlockPos.ofFloored(s.getX(), s.getY(), s.getZ()), "Zvuk");
        }
    }

    private void add(BlockPos pos, String type) {
        if (hits.put(pos, System.currentTimeMillis()) == null && chat.get()) {
            info("%s na (%d, %d, %d)", type, pos.getX(), pos.getY(), pos.getZ());
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(e -> now - e.getValue() > lifetime.get() * 1000L);
        for (BlockPos pos : hits.keySet()) {
            event.renderer.box(pos, color.get(), color.get(), ShapeMode.Both, 0);
        }
    }
}
