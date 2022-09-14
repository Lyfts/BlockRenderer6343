package blockrenderer6343.integration.gregtech;

import blockrenderer6343.BlockRenderer6343;
import blockrenderer6343.api.utils.BlockPosition;
import blockrenderer6343.api.utils.CreativeItemSource;
import blockrenderer6343.client.renderer.GlStateManager;
import blockrenderer6343.client.renderer.ImmediateWorldSceneRenderer;
import blockrenderer6343.client.renderer.WorldSceneRenderer;
import blockrenderer6343.client.world.ClientFakePlayer;
import blockrenderer6343.client.world.TrackedDummyWorld;
import blockrenderer6343.mixins.GuiContainerMixin;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.math.MathHelper;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.guihook.GuiContainerManager;
import com.gtnewhorizon.structurelib.alignment.constructable.ConstructableUtility;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructableProvider;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.mojang.authlib.GameProfile;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.ITurnable;
import gregtech.api.metatileentity.implementations.GT_MetaTileEntity_MultiBlockBase;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Vector3f;

public class GT_GUI_MultiblocksHandler {
    private static ImmediateWorldSceneRenderer renderer;

    public static final int SLOT_SIZE = 18;
    public static final int SLOTS_X = 5;
    public static final int SLOTS_Y = 135;

    private static final int recipeLayoutx = 8;
    private static final int recipeLayouty = 50;
    private static final int recipeWidth = 160;
    private static final int sceneHeight = recipeWidth - 10;
    private static int guiMouseX;
    private static int guiMouseY;
    private static int lastGuiMouseX;
    private static int lastGuiMouseY;
    private static Vector3f center;
    private static float rotationYaw;
    private static float rotationPitch;
    private static float zoom;

    private static ItemStack tooltipBlockStack;
    private static BlockPosition selected;
    private static GT_MetaTileEntity_MultiBlockBase renderingController;
    private static GT_MetaTileEntity_MultiBlockBase lastRenderingController;
    private static int layerIndex = -1;
    private static int tierIndex = 1;
    private List<ItemStack> ingredients = new ArrayList<>();

    private static final int ICON_SIZE_X = 20;
    private static final int ICON_SIZE_Y = 20;
    private static final int mouseOffsetX = 5;
    private static final int mouseOffsetY = 43;
    private static final int buttonsEndPosX = 165;
    private static final int buttonsEndPosY = 155;
    private static final int buttonsStartPosX = buttonsEndPosX - ICON_SIZE_X * 2 - 10;
    private static final int buttonsStartPosY = buttonsEndPosY - ICON_SIZE_Y * 2 - 10;
    private static final Map<GuiButton, Runnable> buttons = new HashMap<>();

    private Consumer<List<ItemStack>> onIngredientChanged;

    private static EntityPlayer fakeMultiblockBuilder;

    public GT_GUI_MultiblocksHandler() {
        GuiButton previousLayerButton =
            new GuiButton(0, buttonsStartPosX, buttonsEndPosY - ICON_SIZE_Y, ICON_SIZE_X, ICON_SIZE_Y, "<");
        GuiButton nextLayerButton = new GuiButton(
            0, buttonsEndPosX - ICON_SIZE_X, buttonsEndPosY - ICON_SIZE_Y, ICON_SIZE_X, ICON_SIZE_Y, ">");
        GuiButton previousTierButton =
            new GuiButton(0, buttonsStartPosX, buttonsStartPosY, ICON_SIZE_X, ICON_SIZE_Y, "<");
        GuiButton nextTierButton =
            new GuiButton(0, buttonsEndPosX - ICON_SIZE_X, buttonsStartPosY, ICON_SIZE_X, ICON_SIZE_Y, ">");
        GuiButton projectMultiblocksButton =
            new GuiButton(0, buttonsEndPosX - ICON_SIZE_X, 5, ICON_SIZE_X, ICON_SIZE_Y, "P");

        buttons.clear();

        buttons.put(previousLayerButton, this::togglePreviousLayer);
        buttons.put(nextLayerButton, this::toggleNextLayer);
        buttons.put(previousTierButton, this::togglePreviousTier);
        buttons.put(nextTierButton, this::toggleNextTier);
        buttons.put(projectMultiblocksButton, this::projectMultiblocks);
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    public GT_GUI_MultiblocksHandler setOnIngredientChanged(Consumer<List<ItemStack>> callback) {
        onIngredientChanged = callback;
        return this;
    }

    public void loadMultiblocks(GT_MetaTileEntity_MultiBlockBase multiblocks) {
        renderingController = multiblocks;
        if (lastRenderingController == null || lastRenderingController != renderingController) {
            tierIndex = 1;
            layerIndex = -1;
            initializeSceneRenderer(tierIndex, true);
        } else {
            initializeSceneRenderer(tierIndex, false);
        }
        lastRenderingController = renderingController;
    }

    private void toggleNextLayer() {
        int height = (int) ((TrackedDummyWorld) renderer.world).getSize().getY() - 1;
        if (++layerIndex > height) {
            // if current layer index is more than max height, reset it
            // to display all layers
            layerIndex = -1;
        }
        setNextLayer(layerIndex);
    }

    private void togglePreviousLayer() {
        int height = (int) ((TrackedDummyWorld) renderer.world).getSize().getY() - 1;
        if (layerIndex == -1) {
            layerIndex = height;
        } else if (--layerIndex < 0) {
            layerIndex = -1;
        }
        setNextLayer(layerIndex);
    }

    private void setNextLayer(int newLayer) {
        layerIndex = newLayer;
        if (renderer != null) {
            TrackedDummyWorld world = ((TrackedDummyWorld) renderer.world);
            resetCenter();
            renderer.renderedBlocks.clear();
            int minY = (int) world.getMinPos().getY();
            List<BlockPosition> renderBlocks;
            if (newLayer == -1) {
                renderBlocks = world.placedBlocks;
                renderer.setRenderAllFaces(false);
            } else {
                renderBlocks = world.placedBlocks.stream()
                    .filter(pos -> pos.y - minY == newLayer)
                    .collect(Collectors.toList());
                renderer.setRenderAllFaces(true);
            }
            renderer.addRenderedBlocks(renderBlocks);
            scanIngredients();
        }
    }

    private void toggleNextTier() {
        initializeSceneRenderer(++tierIndex, false);
    }

    private void togglePreviousTier() {
        if (tierIndex > 1) initializeSceneRenderer(--tierIndex, false);
    }

    private void projectMultiblocks() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        World baseWorld = Minecraft.getMinecraft().theWorld;
        MovingObjectPosition lookingPos = player.rayTrace(10, 1);
        if (lookingPos.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) return;
        int playerDir = MathHelper.floor_double((player.rotationYaw * 4F) / 360F + 0.5D) & 3;
        ItemStack itemStack = renderingController.getStackForm(1);
        if (!baseWorld.isAirBlock(lookingPos.blockX, lookingPos.blockY + 1, lookingPos.blockZ)) return;
        itemStack
            .getItem()
            .onItemUse(
                itemStack,
                player,
                baseWorld,
                lookingPos.blockX,
                lookingPos.blockY + 1,
                lookingPos.blockZ,
                0,
                lookingPos.blockX,
                lookingPos.blockY,
                lookingPos.blockZ);
        ConstructableUtility.handle(
            renderingController.getStackForm(tierIndex),
            player,
            baseWorld,
            lookingPos.blockX,
            lookingPos.blockY + 1,
            lookingPos.blockZ,
            playerDir);
        baseWorld.setBlockToAir(lookingPos.blockX, lookingPos.blockY + 1, lookingPos.blockZ);
        baseWorld.removeTileEntity(lookingPos.blockX, lookingPos.blockY + 1, lookingPos.blockZ);
    }

    private void resetCenter() {
        TrackedDummyWorld world = (TrackedDummyWorld) renderer.world;
        Vector3f size = world.getSize();
        Vector3f minPos = world.getMinPos();
        center = new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2);
        renderer.setCameraLookAt(center, zoom, Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
    }

    public void drawMultiblock() {

        guiMouseX = GuiDraw.getMousePosition().x;
        guiMouseY = GuiDraw.getMousePosition().y;
        int k = (NEIClientUtils.getGuiContainer().width
            - ((GuiContainerMixin) NEIClientUtils.getGuiContainer()).getXSize())
            / 2;
        int l = (NEIClientUtils.getGuiContainer().height
            - ((GuiContainerMixin) NEIClientUtils.getGuiContainer()).getYSize())
            / 2;
        renderer.render(recipeLayoutx + k, recipeLayouty + l, recipeWidth, sceneHeight, lastGuiMouseX, lastGuiMouseY);
        drawMultiblockName();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        tooltipBlockStack = null;

        MovingObjectPosition rayTraceResult = renderer.getLastTraceResult();
        boolean insideView = guiMouseX >= k + recipeLayoutx
            && guiMouseY >= l + recipeLayouty
            && guiMouseX < k + recipeLayoutx + recipeWidth
            && guiMouseY < l + recipeLayouty + sceneHeight;
        boolean leftClickHeld = Mouse.isButtonDown(0);
        boolean rightClickHeld = Mouse.isButtonDown(1);
        if (insideView) {
            if (leftClickHeld) {
                rotationPitch += guiMouseX - lastGuiMouseX + 360;
                rotationPitch = rotationPitch % 360;
                rotationYaw = (float) MathHelper.clip(rotationYaw + (guiMouseY - lastGuiMouseY), -89.9, 89.9);
            } else if (rightClickHeld) {
                int mouseDeltaY = guiMouseY - lastGuiMouseY;
                if (Math.abs(mouseDeltaY) > 1) {
                    zoom = (float) MathHelper.clip(zoom + (mouseDeltaY > 0 ? 0.5 : -0.5), 3, 999);
                }
            }
            renderer.setCameraLookAt(center, zoom, Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
        }

        // draw buttons
        for (GuiButton button : buttons.keySet()) {
            button.drawButton(Minecraft.getMinecraft(), guiMouseX - k - mouseOffsetX, guiMouseY - l - mouseOffsetY);
        }
        drawButtonsTitle();

        if (!(leftClickHeld || rightClickHeld)
            && rayTraceResult != null
            && !renderer.world.isAirBlock(rayTraceResult.blockX, rayTraceResult.blockY, rayTraceResult.blockZ)) {
            Block block = renderer.world.getBlock(rayTraceResult.blockX, rayTraceResult.blockY, rayTraceResult.blockZ);
            tooltipBlockStack = block.getPickBlock(
                rayTraceResult,
                renderer.world,
                rayTraceResult.blockX,
                rayTraceResult.blockY,
                rayTraceResult.blockZ,
                Minecraft.getMinecraft().thePlayer);
        }

        // draw ingredients again because it was hidden by worldrenderer
        drawIngredients();

        lastGuiMouseX = guiMouseX;
        lastGuiMouseY = guiMouseY;

        //        don't activate these
        //        GlStateManager.disableRescaleNormal();
        //        GlStateManager.disableLighting();
        //        RenderHelper.disableStandardItemLighting();
    }

    private void drawMultiblockName() {
        String localizedName = I18n.format(renderingController.getStackForm(1).getDisplayName());
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        List<String> lines =
            fontRenderer.listFormattedStringToWidth(localizedName, GT_GUI_MultiblocksHandler.recipeWidth - 10);
        for (int i = 0; i < lines.size(); i++) {
            fontRenderer.drawString(
                lines.get(i),
                (GT_GUI_MultiblocksHandler.recipeWidth - fontRenderer.getStringWidth(lines.get(i))) / 2,
                fontRenderer.FONT_HEIGHT * i,
                0x333333);
        }
    }

    private void drawButtonsTitle() {
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        String tierText = "Tier: " + tierIndex;
        fontRenderer.drawString(
            tierText,
            buttonsStartPosX + (buttonsEndPosX - buttonsStartPosX - fontRenderer.getStringWidth(tierText)) / 2,
            buttonsStartPosY - 10,
            0x333333);
        String layerText = "Layer: " + (layerIndex == -1 ? "A" : Integer.toString(layerIndex + 1));
        fontRenderer.drawString(
            layerText,
            buttonsStartPosX + (buttonsEndPosX - buttonsStartPosX - fontRenderer.getStringWidth(layerText)) / 2,
            buttonsStartPosY + ICON_SIZE_Y,
            0x333333);
    }

    private void drawIngredients() {
        for (int i = 0; i < ingredients.size(); i++)
            GuiContainerManager.drawItem(SLOTS_X + i * SLOT_SIZE, SLOTS_Y, ingredients.get(i));
    }

    private void initializeSceneRenderer(int tier, boolean resetCamera) {
        Vector3f eyePos = new Vector3f(), lookAt = new Vector3f(), worldUp = new Vector3f();
        if (!resetCamera) {
            try {
                eyePos = renderer.getEyePos();
                lookAt = renderer.getLookAt();
                worldUp = renderer.getWorldUp();
            } catch (NullPointerException e) {
                BlockRenderer6343.error("please reset camera on your first renderer call!");
            }
        }

        renderer = new ImmediateWorldSceneRenderer(new TrackedDummyWorld());
        renderer.world.updateEntities();
        renderer.setClearColor(0xC6C6C6);

        placeMultiblocks(tier);

        Vector3f size = ((TrackedDummyWorld) renderer.world).getSize();
        Vector3f minPos = ((TrackedDummyWorld) renderer.world).getMinPos();
        center = new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2);

        renderer.renderedBlocks.clear();
        renderer.addRenderedBlocks(((TrackedDummyWorld) renderer.world).placedBlocks);
        renderer.setOnLookingAt(ray -> {
        });

        renderer.setOnWorldRender(this::onRender);
        //        world.setRenderFilter(pos -> worldSceneRenderer.renderedBlocksMap.keySet().stream().anyMatch(c ->
        // c.contains(pos)));

        selected = null;
        setNextLayer(layerIndex);

        if (resetCamera) {
            float max = Math.max(Math.max(Math.max(size.x, size.y), size.z), 1);
            zoom = (float) (3.5 * Math.sqrt(max));
            rotationYaw = 20.0f;
            rotationPitch = 50f;
            if (renderer != null) {
                resetCenter();
            }
        } else {
            renderer.setCameraLookAt(eyePos, lookAt, worldUp);
        }
    }

    private void placeMultiblocks(int tier) {
        fakeMultiblockBuilder = new ClientFakePlayer(
            renderer.world, new GameProfile(UUID.fromString("518FDF18-EC2A-4322-832A-58ED1721309B"), "[GregTech]"));
        renderer.world.unloadEntities(Arrays.asList(fakeMultiblockBuilder));

        IConstructable constructable = null;
        BlockPosition mbBlockPos = new BlockPosition(10, 10, 10);

        ItemStack itemStack = renderingController.getStackForm(1);
        itemStack
            .getItem()
            .onItemUse(
                itemStack,
                fakeMultiblockBuilder,
                renderer.world,
                mbBlockPos.x,
                mbBlockPos.y,
                mbBlockPos.z,
                0,
                mbBlockPos.x,
                mbBlockPos.y,
                mbBlockPos.z);

        TileEntity tTileEntity = renderer.world.getTileEntity(mbBlockPos.x, mbBlockPos.y, mbBlockPos.z);
        ((ITurnable) tTileEntity).setFrontFacing((byte) 3);
        IMetaTileEntity mte = ((IGregTechTileEntity) tTileEntity).getMetaTileEntity();

        if (mte instanceof ISurvivalConstructable) {
            int result;
            do {
                result = ((ISurvivalConstructable) mte)
                    .survivalConstruct(
                        renderingController.getStackForm(tier),
                        Integer.MAX_VALUE,
                        ISurvivalBuildEnvironment.create(CreativeItemSource.instance, fakeMultiblockBuilder));
            } while (result > 0);
        } else if (tTileEntity instanceof IConstructableProvider) {
            constructable = ((IConstructableProvider) tTileEntity).getConstructable();
        } else if (tTileEntity instanceof IConstructable) {
            constructable = (IConstructable) tTileEntity;
        }
        if (constructable != null) {
            constructable.construct(renderingController.getStackForm(tier), false);
        }
    }

    public void onRender(WorldSceneRenderer renderer) {
        BlockPosition look = renderer.getLastTraceResult() == null
            ? null
            : new BlockPosition(
            renderer.getLastTraceResult().blockX,
            renderer.getLastTraceResult().blockY,
            renderer.getLastTraceResult().blockZ);
        if (look != null && look.equals(selected)) {
            renderBlockOverLay(selected, Blocks.glass.getIcon(0,6));
            return;
        }
        renderBlockOverLay(look, Blocks.stained_glass.getIcon(0,7));
        renderBlockOverLay(selected, Blocks.stained_glass.getIcon(0,14));
    }

    private void scanIngredients() {
        List<ItemStack> ingredients = new ArrayList<>();
        for (BlockPosition renderedBlock : renderer.renderedBlocks) {
            Block block = renderer.world.getBlock(renderedBlock.x, renderedBlock.y, renderedBlock.z);
            if (block.equals(Blocks.air)) continue;
            int meta = renderer.world.getBlockMetadata(renderedBlock.x, renderedBlock.y, renderedBlock.z);
            ArrayList<ItemStack> itemstacks =
                block.getDrops(renderer.world, renderedBlock.x, renderedBlock.y, renderedBlock.z, meta, 0);
            if (itemstacks.size() == 0) { // glass
                itemstacks.add(new ItemStack(block));
            }
            boolean added = false;
            for (ItemStack ingredient : ingredients) {
                if (NEIClientUtils.areStacksSameTypeWithNBT(ingredient, itemstacks.get(0))) {
                    ingredient.stackSize++;
                    added = true;
                    break;
                }
            }
            if (!added) ingredients.add(itemstacks.get(0));
        }
        this.ingredients = ingredients;

        if (onIngredientChanged != null) {
            onIngredientChanged.accept(ingredients);
        }
    }

    @SideOnly(Side.CLIENT)
    private void renderBlockOverLay(BlockPosition pos, IIcon icon) {
        if(pos == null)
            return;

        RenderBlocks bufferBuilder = new RenderBlocks();
        bufferBuilder.blockAccess = renderer.world;
        bufferBuilder.setRenderBounds(0, 0, 0, 1, 1, 1);
        bufferBuilder.renderAllFaces = true;
        Block block = renderer.world.getBlock(pos.x, pos.y, pos.z);
        bufferBuilder.renderBlockUsingTexture(block, pos.x, pos.y, pos.z, icon);
    }

    public static int packColor(int red, int green, int blue, int alpha) {
        return (red & 0xFF) << 24 | (green & 0xFF) << 16 | (blue & 0xFF) << 8 | (alpha & 0xFF);
    }

    public boolean mouseClicked(int button) {
        for (Map.Entry<GuiButton, Runnable> buttons : buttons.entrySet()) {
            int k = (NEIClientUtils.getGuiContainer().width
                - ((GuiContainerMixin) NEIClientUtils.getGuiContainer()).getXSize())
                / 2;
            int l = (NEIClientUtils.getGuiContainer().height
                - ((GuiContainerMixin) NEIClientUtils.getGuiContainer()).getYSize())
                / 2;
            if (buttons.getKey()
                .mousePressed(
                    Minecraft.getMinecraft(), guiMouseX - k - mouseOffsetX, guiMouseY - l - mouseOffsetY)) {
                buttons.getValue().run();
                selected = null;
                return true;
            }
        }
        if (button == 1 && renderer != null) {
            if (renderer.getLastTraceResult() == null) {
                if (selected != null) {
                    selected = null;
                    return true;
                }
                return false;
            }
            selected = new BlockPosition(
                renderer.getLastTraceResult().blockX,
                renderer.getLastTraceResult().blockY,
                renderer.getLastTraceResult().blockZ);
        }
        return false;
    }

    public List<String> handleTooltip() {
        if (tooltipBlockStack != null)
            return tooltipBlockStack.getTooltip(
                Minecraft.getMinecraft().thePlayer, Minecraft.getMinecraft().gameSettings.advancedItemTooltips);
        else return null;
    }
}
