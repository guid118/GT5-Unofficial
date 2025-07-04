package tectech.thing.metaTileEntity.multi.godforge;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static gregtech.api.util.GTRecipeConstants.FOG_PLASMA_MULTISTEP;
import static gregtech.api.util.GTRecipeConstants.FOG_PLASMA_TIER;
import static gregtech.api.util.GTUtility.formatNumbers;
import static gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap;
import static gregtech.common.misc.WirelessNetworkManager.getUserEU;
import static net.minecraft.util.EnumChatFormatting.GREEN;
import static net.minecraft.util.EnumChatFormatting.RED;
import static net.minecraft.util.EnumChatFormatting.RESET;
import static net.minecraft.util.EnumChatFormatting.YELLOW;

import java.math.BigInteger;
import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;

import gregtech.api.enums.SoundResource;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import tectech.loader.ConfigHandler;
import tectech.recipe.TecTechRecipeMaps;
import tectech.thing.gui.TecTechUITextures;

public class MTEPlasmaModule extends MTEBaseModule {

    private long EUt = 0;
    private int currentParallel = 0;
    private int inputMaxParallel = 0;

    private static final int DEBUG_WINDOW_ID = 11;

    public MTEPlasmaModule(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEPlasmaModule(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPlasmaModule(mName);
    }

    long wirelessEUt = 0;

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            @NotNull
            @Override
            protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                wirelessEUt = (long) recipe.mEUt * getActualParallel();
                if (getUserEU(userUUID).compareTo(BigInteger.valueOf(wirelessEUt * recipe.mDuration)) < 0) {
                    return CheckRecipeResultRegistry.insufficientPower(wirelessEUt * recipe.mDuration);
                }
                if (recipe.getMetadataOrDefault(FOG_PLASMA_TIER, 0) > getPlasmaTier()
                    || (recipe.getMetadataOrDefault(FOG_PLASMA_MULTISTEP, false) && !isMultiStepPlasmaCapable)) {
                    return SimpleCheckRecipeResult.ofFailure("missing_upgrades");
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @NotNull
            @Override
            protected CheckRecipeResult onRecipeStart(@NotNull GTRecipe recipe) {
                wirelessEUt = (long) recipe.mEUt * maxParallel;
                if (!addEUToGlobalEnergyMap(userUUID, -calculatedEut * duration)) {
                    return CheckRecipeResultRegistry.insufficientPower(wirelessEUt * recipe.mDuration);
                }
                addToPowerTally(
                    BigInteger.valueOf(calculatedEut)
                        .multiply(BigInteger.valueOf(duration)));
                addToRecipeTally(calculatedParallels);
                currentParallel = calculatedParallels;
                EUt = calculatedEut;
                overwriteCalculatedEut(0);
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @NotNull
            @Override
            protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
                return super.createOverclockCalculator(recipe).setEUt(getSafeProcessingVoltage())
                    .setDurationDecreasePerOC(getOverclockTimeFactor());
            }
        };
    }

    @Override
    protected void setProcessingLogicPower(ProcessingLogic logic) {
        logic.setAvailableVoltage(Long.MAX_VALUE);
        logic.setAvailableAmperage(Integer.MAX_VALUE);
        logic.setAmperageOC(false);
        logic.setUnlimitedTierSkips();
        logic.setMaxParallel(getActualParallel());
        logic.setSpeedBonus(getSpeedBonus());
        logic.setEuModifier(getEnergyDiscount());
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        super.addUIWidgets(builder, buildContext);
        buildContext.addSyncedWindow(DEBUG_WINDOW_ID, this::createDebugWindow);
        if (ConfigHandler.debug.DEBUG_MODE) {
            builder.widget(createDebugWindowButton());
        }
    }

    protected ButtonWidget createDebugWindowButton() {
        Widget button = new ButtonWidget().setOnClick(
            (data, widget) -> {
                if (!widget.isClient()) widget.getContext()
                    .openSyncedWindow(DEBUG_WINDOW_ID);
            })
            .setPlayClickSound(false)
            .setBackground(TecTechUITextures.BUTTON_CELESTIAL_32x32, TecTechUITextures.OVERLAY_BUTTON_LOAF_MODE)
            .addTooltip(StatCollector.translateToLocal("tt.gui.tooltip.plasma_module.debug_window"))
            .setTooltipShowUpDelay(TOOLTIP_DELAY)
            .setPos(174, 91)
            .setSize(16, 16);
        return (ButtonWidget) button;
    }

    protected ModularWindow createDebugWindow(final EntityPlayer player) {
        final int WIDTH = 78;
        final int HEIGHT = 60;
        final int PARENT_WIDTH = getGUIWidth();
        final int PARENT_HEIGHT = getGUIHeight();
        ModularWindow.Builder builder = ModularWindow.builder(WIDTH, HEIGHT);
        builder.setBackground(GTUITextures.BACKGROUND_SINGLEBLOCK_DEFAULT);
        builder.setGuiTint(getGUIColorization());
        builder.setDraggable(true);
        builder.setPos(
            (size, window) -> Alignment.Center.getAlignedPos(size, new Size(PARENT_WIDTH, PARENT_HEIGHT))
                .add(
                    Alignment.TopRight.getAlignedPos(new Size(PARENT_WIDTH, PARENT_HEIGHT), new Size(WIDTH, HEIGHT))
                        .add(WIDTH - 3, 0)));

        // Debug enable/disable multi-step
        builder.widget(
            new ButtonWidget().setOnClick((clickData, widget) -> isMultiStepPlasmaCapable = !isMultiStepPlasmaCapable)
                .setPlayClickSoundResource(
                    () -> isAllowedToWork() ? SoundResource.GUI_BUTTON_UP.resourceLocation
                        : SoundResource.GUI_BUTTON_DOWN.resourceLocation)
                .setBackground(() -> {
                    if (isMultiStepPlasmaCapable) {
                        return new IDrawable[] { GTUITextures.BUTTON_STANDARD_PRESSED,
                            GTUITextures.OVERLAY_BUTTON_POWER_SWITCH_ON };
                    } else {
                        return new IDrawable[] { GTUITextures.BUTTON_STANDARD,
                            GTUITextures.OVERLAY_BUTTON_POWER_SWITCH_OFF };
                    }
                })
                .attachSyncer(new FakeSyncWidget.BooleanSyncer(this::isAllowedToWork, val -> {
                    if (val) enableWorking();
                    else disableWorking();
                }), builder)
                .addTooltip(StatCollector.translateToLocal("tt.gui.tooltip.plasma_module.debug_window.multi_step"))
                .setTooltipShowUpDelay(TOOLTIP_DELAY)
                .setPos(4, 40)
                .setSize(16, 16));

        // Debug set plasma tier
        builder.widget(
            new TextFieldWidget().setSetterInt(this::setPlasmaTier)
                .setGetterInt(this::getPlasmaTier)
                .setNumbers(0, 2)
                .setTextAlignment(Alignment.Center)
                .setTextColor(Color.WHITE.normal)
                .setPos(3, 18)
                .addTooltip(StatCollector.translateToLocal("tt.gui.tooltip.plasma_module.debug_window.fusion_tier"))
                .setTooltipShowUpDelay(TOOLTIP_DELAY)
                .setSize(16, 16)
                .setPos(4, 20)
                .setBackground(GTUITextures.BACKGROUND_TEXT_FIELD));

        // Debug set max parallel
        builder.widget(
            new TextFieldWidget().setSetterInt(val -> inputMaxParallel = val)
                .setGetterInt(() -> inputMaxParallel)
                .setNumbers(0, Integer.MAX_VALUE)
                .setTextAlignment(Alignment.Center)
                .setTextColor(Color.WHITE.normal)
                .setPos(3, 18)
                .addTooltip(StatCollector.translateToLocal("tt.gui.tooltip.plasma_module.debug_window.parallel"))
                .setTooltipShowUpDelay(TOOLTIP_DELAY)
                .setSize(70, 16)
                .setPos(4, 4)
                .setBackground(GTUITextures.BACKGROUND_TEXT_FIELD));

        return builder.build();
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return TecTechRecipeMaps.godforgePlasmaRecipes;
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> str = new ArrayList<>();
        str.add(
            StatCollector.translateToLocalFormatted(
                "GT5U.infodata.progress",
                GREEN + formatNumbers(mProgresstime / 20) + RESET,
                YELLOW + formatNumbers(mMaxProgresstime / 20) + RESET));
        str.add(
            StatCollector.translateToLocalFormatted(
                "tt.infodata.multi.currently_using",
                RED + (getBaseMetaTileEntity().isActive() ? formatNumbers(EUt) : "0") + RESET));
        str.add(
            YELLOW + StatCollector.translateToLocalFormatted(
                "tt.infodata.multi.max_parallel",
                RESET + formatNumbers(getActualParallel())));
        str.add(
            YELLOW + StatCollector.translateToLocalFormatted(
                "GT5U.infodata.parallel.current",
                RESET + (getBaseMetaTileEntity().isActive() ? formatNumbers(currentParallel) : "0")));
        str.add(
            YELLOW + StatCollector.translateToLocalFormatted(
                "tt.infodata.multi.multiplier.recipe_time",
                RESET + formatNumbers(getSpeedBonus())));
        str.add(
            YELLOW + StatCollector.translateToLocalFormatted(
                "tt.infodata.multi.multiplier.energy",
                RESET + formatNumbers(getEnergyDiscount())));
        str.add(
            YELLOW + StatCollector.translateToLocalFormatted(
                "tt.infodata.multi.divisor.recipe_time.non_perfect_oc",
                RESET + formatNumbers(getOverclockTimeFactor())));
        return str.toArray(new String[0]);
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Plasma Fabricator")
            .addInfo("This is a module of the Godforge.")
            .addInfo("Must be part of a Godforge to function.")
            .addInfo("Used for extreme temperature matter ionization.")
            .addSeparator(EnumChatFormatting.AQUA, 74)
            .addInfo("The third module of the Godforge, this module infuses materials with extreme amounts")
            .addInfo("of heat, ionizing and turning them into plasma directly. Not all plasmas can be produced")
            .addInfo("right away, some of them require certain upgrades to be unlocked.")
            .addInfo("This module is specialized towards energy and overclock efficiency.")
            .beginStructureBlock(7, 7, 13, false)
            .addStructureInfo(
                EnumChatFormatting.GOLD + "20"
                    + EnumChatFormatting.GRAY
                    + " Singularity Reinforced Stellar Shielding Casing")
            .addStructureInfo(
                EnumChatFormatting.GOLD + "20"
                    + EnumChatFormatting.GRAY
                    + " Boundless Gravitationally Severed Structure Casing")
            .addStructureInfo(
                EnumChatFormatting.GOLD + "5" + EnumChatFormatting.GRAY + " Harmonic Phonon Transmission Conduit")
            .addStructureInfo(
                EnumChatFormatting.GOLD + "5" + EnumChatFormatting.GRAY + " Celestial Matter Guidance Casing")
            .addStructureInfo(EnumChatFormatting.GOLD + "1" + EnumChatFormatting.GRAY + " Stellar Energy Siphon Casing")
            .toolTipFinisher(EnumChatFormatting.AQUA, 74);
        return tt;
    }

}
