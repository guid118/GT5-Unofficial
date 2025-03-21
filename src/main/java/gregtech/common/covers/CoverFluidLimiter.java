package gregtech.common.covers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import org.jetbrains.annotations.NotNull;

import com.google.common.io.ByteArrayDataInput;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.covers.CoverContext;
import gregtech.api.gui.modularui.CoverUIBuildContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.util.GTUtility;
import gregtech.common.gui.modularui.widget.CoverDataControllerWidget;
import gregtech.common.gui.modularui.widget.CoverDataFollowerNumericWidget;
import io.netty.buffer.ByteBuf;

/***
 * @author TrainerSnow#5086
 */
public class CoverFluidLimiter extends CoverBehaviorBase {

    private float threshold;

    public CoverFluidLimiter(CoverContext context, ITexture coverTexture) {
        super(context, coverTexture);
        initializeData(context.getCoverInitializer());
    }

    public float getThreshold() {
        return this.threshold;
    }

    public CoverFluidLimiter setThreshold(float threshold) {
        this.threshold = threshold;
        return this;
    }

    @Override
    protected void initializeData() {
        threshold = 1F;
    }

    @Override
    protected void loadFromNbt(NBTBase nbt) {
        if (nbt instanceof NBTTagCompound tag) {
            this.threshold = tag.getFloat("threshold");
        }
    }

    @Override
    protected void readFromPacket(ByteArrayDataInput byteData) {
        this.threshold = byteData.readFloat();
    }

    @Override
    protected @NotNull NBTBase saveDataToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setFloat("threshold", this.threshold);
        return tag;
    }

    @Override
    protected void writeDataToByteBuf(ByteBuf byteBuf) {
        byteBuf.writeFloat(this.threshold);
    }

    @Override
    public void onCoverScrewdriverClick(EntityPlayer aPlayer, float aX, float aY, float aZ) {
        if (coveredTile.get() instanceof IFluidHandler) {
            adjustThreshold(!aPlayer.isSneaking());
            GTUtility.sendChatToPlayer(aPlayer, String.format("Threshold: %f", threshold));
        }
    }

    @Override
    public boolean letsFluidIn(Fluid aFluid) {
        if (coveredTile.get() instanceof IFluidHandler fluidHandler) {
            return threshold > getFillLevelInputSlots(fluidHandler);
        }
        return false;
    }

    @Override
    public boolean alwaysLookConnected() {
        return true;
    }

    /*
     * Helpers
     */

    private void adjustThreshold(boolean way) {
        if (way) {
            if ((threshold + 0.05f) > 1F) {
                threshold = 0F;
                return;
            }
            threshold += 0.05F;
        } else {
            if ((Math.abs(threshold) - 0.05F) < 0F) {
                threshold = 1F;
                return;
            }
            threshold -= 0.05F;
        }
    }

    private float getFillLevelInputSlots(IFluidHandler fh) {
        FluidTankInfo[] tankInfo = fh.getTankInfo(ForgeDirection.UNKNOWN);
        long tMax;
        long tUsed;
        if (tankInfo != null) {
            // 0 Because we acces first slot only
            FluidTankInfo inputSlot = tankInfo[0];
            if (inputSlot.fluid != null) {
                tMax = inputSlot.capacity;
                tUsed = inputSlot.fluid.amount;
                return (float) tUsed / (float) tMax;
            }
        }
        return 0F;
    }

    // GUI stuff

    @Override
    public boolean hasCoverGUI() {
        return true;
    }

    @Override
    public ModularWindow createWindow(CoverUIBuildContext buildContext) {
        return new FluidLimiterUIFactory(buildContext).createWindow();
    }

    private static class FluidLimiterUIFactory extends UIFactory<CoverFluidLimiter> {

        private static final int startX = 10;
        private static final int startY = 25;
        private static final int spaceX = 18;
        private static final int spaceY = 18;

        public FluidLimiterUIFactory(CoverUIBuildContext buildContext) {
            super(buildContext);
        }

        @Override
        protected CoverFluidLimiter adaptCover(Cover cover) {
            if (cover instanceof CoverFluidLimiter adapterCover) {
                return adapterCover;
            }
            return null;
        }

        @Override
        protected void addUIWidgets(ModularWindow.Builder builder) {
            builder
                .widget(
                    new CoverDataControllerWidget<>(this::getCover, getUIBuildContext()).addFollower(
                        new CoverDataFollowerNumericWidget<>(),
                        coverData -> (double) Math.round(coverData.getThreshold() * 100),
                        (coverData, val) -> coverData.setThreshold(val.floatValue() / 100),
                        widget -> widget.setBounds(0, 100)
                            .setFocusOnGuiOpen(true)
                            .setPos(startX, startY + spaceY * 2 - 24)
                            .setSize(spaceX * 4 - 3, 12)))
                .widget(
                    new TextWidget("Percent threshold").setDefaultColor(COLOR_TEXT_GRAY.get())
                        .setPos(startX, startY + spaceY * 2 - 35));
        }
    }
}
