package com.musix.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * v5.3.0: 상자 GUI 의 실제 그려지는 사각형 좌표 접근용.
 * "GUI 밖 클릭 = 음량 조절" 판정에 필요한데 x/y/backgroundWidth/backgroundHeight 가
 * 모두 protected 라 Accessor 로 노출한다.
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int musix$getX();

    @Accessor("y")
    int musix$getY();

    @Accessor("backgroundWidth")
    int musix$getBackgroundWidth();

    @Accessor("backgroundHeight")
    int musix$getBackgroundHeight();
}
