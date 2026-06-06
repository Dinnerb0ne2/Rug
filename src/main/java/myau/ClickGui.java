package myau.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.Myau;
import myau.ui.animation.Animation;
import myau.ui.animation.Easing;
import myau.module.Module;
import myau.module.modules.*;
import myau.property.Property;
import myau.property.properties.*;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class ClickGui extends GuiScreen {

    private static final Color COLOR_BACKGROUND = new Color(25, 25, 25, 180);
    private static final Color COLOR_HEADER = new Color(34, 34, 34);
    private static final Color COLOR_TEXT_FIELD_BG = new Color(34, 34, 34);
    private static final Color COLOR_TEXT_FIELD_FG = new Color(45, 45, 45, 200);
    private static final Color COLOR_SLIDER_BG = new Color(34, 34, 34);
    private static final Color COLOR_SEPARATOR = new Color(34, 34, 34);
    private static final Color COLOR_TEXT_PRIMARY = new Color(200, 200, 200);
    private static final Color COLOR_TEXT_SECONDARY = new Color(180, 180, 180);
    private static final Color COLOR_TEXT_HOVER = new Color(220, 220, 220);
    private static final Color COLOR_TEXT_PLACEHOLDER = new Color(120, 120, 120);
    private static final Color COLOR_ENABLED = new Color(0, 180, 0);
    private static final Color COLOR_DISABLED = new Color(180, 0, 0);
    private static final Color COLOR_INDICATOR_WHITE = Color.WHITE;
    private static final Color COLOR_INDICATOR_BLACK = Color.BLACK;

    private static final float WIDTH = 500.0f;
    private static final float HEIGHT = 300.0f;
    private static final float HEADER_HEIGHT = 17.0f;
    private static final float CATEGORY_OFFSET_X = 90.0f;
    private static final float MODULE_LIST_WIDTH = 90.0f;
    private static final float VALUE_AREA_OFFSET_X = 100.0f;
    private static final float TEXT_FIELD_WIDTH = 100.0f;
    private static final float SLIDER_WIDTH = 98.0f;
    private static final float SLIDER_HEIGHT = 10.0f;
    private static final float COLOR_PICKER_WIDTH = 100.0f;
    private static final float COLOR_PICKER_HEIGHT = 50.0f;
    private static final float HUE_SLIDER_HEIGHT = 5.0f;
    private static final float ALPHA_SLIDER_HEIGHT = 5.0f;
    private static final float COLOR_PREVIEW_SIZE = 20.0f;
    private static final float SCROLL_SPEED = 0.1f;
    private static final float ANIMATION_SPEED_GUI = 8.0f;
    private static final float MAX_DELTA_TIME = 0.1f;

    public static float lastPosX = -1337.0f;
    public static float lastPosY = -1337.0f;

    private static String lastCategory = null;
    private static String lastModule = null;
    private static float lastModuleScroll = 0.0f;
    private static float lastValueScroll = 0.0f;

    private boolean dragging = false;
    private boolean waitingForKey = false;
    private boolean draggingSlider = false;
    private boolean isGuiOpen = true;

    private float guiOpenAnimation = 0.0f;
    private long lastAnimationTime = System.currentTimeMillis();
    private float draggingX;
    private float draggingY;
    private float posX = 150.0f;
    private float posY = 80.0f;
    private float valueScroll = 0.0f;
    private float moduleScroll = 0.0f;

    private Module selectedModule;
    private Category selectedCategory = null;
    private Property<?> currentDraggingSlider = null;

    private final HashMap<TextProperty, GuiTextField> textFieldMap = new HashMap<>();
    private final Map<ColorProperty, ColorPickerState> colorPickerStates = new HashMap<>();
    private final HashMap<Property<?>, Float> numberSettingMap = new HashMap<>();

    private final File configFile = new File("./config/Myau/", "clickgui.txt");
    private static ClickGui instance;

    private final Animation categoryLineAnimation = new Animation(Easing.EaseOutQuint, 300L);
    private float lastCategoryLineTargetX = -1337.0f;

    public enum Category {
        COMBAT("Combat"),
        MOVEMENT("Movement"),
        RENDER("Render"),
        PLAYER("Player"),
        MISC("Misc");

        private final String displayName;
        Category(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private final Map<Category, List<Module>> categoryModules = new LinkedHashMap<>();

    private static class ColorPickerState {
        float pickerX, pickerY, hueSliderY, alphaSliderY;
        boolean draggingHue, draggingColor, draggingAlpha;
        float hue, saturation, brightness, alpha;
    }

    public enum GuiEvent {
        DRAW, CLICK, RELEASE
    }

    public ClickGui() {
        instance = this;
        initCategories();

        if (lastPosX != -1337.0f && lastPosY != -1337.0f) {
            this.posX = lastPosX;
            this.posY = lastPosY;
        }
        restoreLastState();
        loadPositions();
    }

    public static ClickGui getInstance() {
        return instance;
    }

    private void initCategories() {
        List<Module> combatModules = new ArrayList<>();
        combatModules.add(Myau.moduleManager.getModule(AimAssist.class));
        combatModules.add(Myau.moduleManager.getModule(AutoClicker.class));
        combatModules.add(Myau.moduleManager.getModule(KillAura.class));
        combatModules.add(Myau.moduleManager.getModule(Wtap.class));
        combatModules.add(Myau.moduleManager.getModule(BackTrack.class));
        combatModules.add(Myau.moduleManager.getModule(TimerRange.class));
        combatModules.add(Myau.moduleManager.getModule(Velocity.class));
        combatModules.add(Myau.moduleManager.getModule(Freeze.class));
        combatModules.add(Myau.moduleManager.getModule(Reach.class));
        combatModules.add(Myau.moduleManager.getModule(TargetStrafe.class));
        combatModules.add(Myau.moduleManager.getModule(AntiFireball.class));
        combatModules.add(Myau.moduleManager.getModule(LagRange.class));
        combatModules.add(Myau.moduleManager.getModule(HitBox.class));
        combatModules.add(Myau.moduleManager.getModule(MoreKB.class));
        combatModules.add(Myau.moduleManager.getModule(Refill.class));
        combatModules.add(Myau.moduleManager.getModule(HitSelect.class));

        List<Module> movementModules = new ArrayList<>();
        movementModules.add(Myau.moduleManager.getModule(AntiAFK.class));
        movementModules.add(Myau.moduleManager.getModule(Fly.class));
        movementModules.add(Myau.moduleManager.getModule(Speed.class));
        movementModules.add(Myau.moduleManager.getModule(LongJump.class));
        movementModules.add(Myau.moduleManager.getModule(Sprint.class));
        movementModules.add(Myau.moduleManager.getModule(SafeWalk.class));
        movementModules.add(Myau.moduleManager.getModule(Jesus.class));
        movementModules.add(Myau.moduleManager.getModule(Blink.class));
        movementModules.add(Myau.moduleManager.getModule(NoFall.class));
        movementModules.add(Myau.moduleManager.getModule(NoSlow.class));
        movementModules.add(Myau.moduleManager.getModule(KeepSprint.class));
        movementModules.add(Myau.moduleManager.getModule(Eagle.class));
        movementModules.add(Myau.moduleManager.getModule(NoJumpDelay.class));
        movementModules.add(Myau.moduleManager.getModule(AntiVoid.class));

        List<Module> renderModules = new ArrayList<>();
        renderModules.add(Myau.moduleManager.getModule(ESP.class));
        renderModules.add(Myau.moduleManager.getModule(Chat.class));
        renderModules.add(Myau.moduleManager.getModule(Chams.class));
        renderModules.add(Myau.moduleManager.getModule(FullBright.class));
        renderModules.add(Myau.moduleManager.getModule(Tracers.class));
        renderModules.add(Myau.moduleManager.getModule(NameTags.class));
        renderModules.add(Myau.moduleManager.getModule(Xray.class));
        renderModules.add(Myau.moduleManager.getModule(ItemPhysics.class));
        renderModules.add(Myau.moduleManager.getModule(TargetHUD.class));
        renderModules.add(Myau.moduleManager.getModule(Indicators.class));
        renderModules.add(Myau.moduleManager.getModule(BedESP.class));
        renderModules.add(Myau.moduleManager.getModule(ItemESP.class));
        renderModules.add(Myau.moduleManager.getModule(ViewClip.class));
        renderModules.add(Myau.moduleManager.getModule(NoHurtCam.class));
        renderModules.add(Myau.moduleManager.getModule(HUD.class));
        renderModules.add(Myau.moduleManager.getModule(GuiModule.class));
        renderModules.add(Myau.moduleManager.getModule(ChestESP.class));
        renderModules.add(Myau.moduleManager.getModule(Trajectories.class));
        renderModules.add(Myau.moduleManager.getModule(Radar.class));

        List<Module> playerModules = new ArrayList<>();
        playerModules.add(Myau.moduleManager.getModule(AutoHeal.class));
        playerModules.add(Myau.moduleManager.getModule(AutoTool.class));
        playerModules.add(Myau.moduleManager.getModule(NoClickDelay.class));
        playerModules.add(Myau.moduleManager.getModule(ChestStealer.class));
        playerModules.add(Myau.moduleManager.getModule(InvManager.class));
        playerModules.add(Myau.moduleManager.getModule(InvWalk.class));
        playerModules.add(Myau.moduleManager.getModule(Scaffold.class));
        playerModules.add(Myau.moduleManager.getModule(AutoBlockIn.class));
        playerModules.add(Myau.moduleManager.getModule(SpeedMine.class));
        playerModules.add(Myau.moduleManager.getModule(FastPlace.class));
        playerModules.add(Myau.moduleManager.getModule(GhostHand.class));
        playerModules.add(Myau.moduleManager.getModule(MCF.class));
        playerModules.add(Myau.moduleManager.getModule(AntiDebuff.class));

        List<Module> miscModules = new ArrayList<>();
        miscModules.add(Myau.moduleManager.getModule(Spammer.class));
        miscModules.add(Myau.moduleManager.getModule(BedNuker.class));
        miscModules.add(Myau.moduleManager.getModule(BedTracker.class));
        miscModules.add(Myau.moduleManager.getModule(LightningTracker.class));
        miscModules.add(Myau.moduleManager.getModule(NoRotate.class));
        miscModules.add(Myau.moduleManager.getModule(NickHider.class));
        miscModules.add(Myau.moduleManager.getModule(AntiObbyTrap.class));
        miscModules.add(Myau.moduleManager.getModule(AntiObfuscate.class));
        miscModules.add(Myau.moduleManager.getModule(AutoAnduril.class));
        miscModules.add(Myau.moduleManager.getModule(InventoryClicker.class));
        miscModules.add(Myau.moduleManager.getModule(ExploitFixer.class));

        combatModules.removeIf(m -> m == null);
        movementModules.removeIf(m -> m == null);
        renderModules.removeIf(m -> m == null);
        playerModules.removeIf(m -> m == null);
        miscModules.removeIf(m -> m == null);

        Comparator<Module> comparator = Comparator.comparing(m -> m.getName().toLowerCase());
        combatModules.sort(comparator);
        movementModules.sort(comparator);
        renderModules.sort(comparator);
        playerModules.sort(comparator);
        miscModules.sort(comparator);

        Set<Module> registered = new HashSet<>();
        registered.addAll(combatModules);
        registered.addAll(movementModules);
        registered.addAll(renderModules);
        registered.addAll(playerModules);
        registered.addAll(miscModules);

        for (Module module : Myau.moduleManager.modules.values()) {
            if (!registered.contains(module)) {
                throw new RuntimeException(module.getClass().getName() + " is unregistered to click gui.");
            }
        }

        categoryModules.put(Category.COMBAT, combatModules);
        categoryModules.put(Category.MOVEMENT, movementModules);
        categoryModules.put(Category.RENDER, renderModules);
        categoryModules.put(Category.PLAYER, playerModules);
        categoryModules.put(Category.MISC, miscModules);
    }

    private void restoreLastState() {
        if (lastCategory != null) {
            for (Category cat : Category.values()) {
                if (cat.name().equals(lastCategory)) {
                    selectedCategory = cat;
                    break;
                }
            }
        }
        if (lastModule != null) {
            List<Module> modulesToSearch = getCurrentModuleList();
            for (Module m : modulesToSearch) {
                if (m.getName().equals(lastModule)) {
                    selectedModule = m;
                    moduleScroll = lastModuleScroll;
                    valueScroll = lastValueScroll;
                    break;
                }
            }
        }
    }

    private List<Module> getCurrentModuleList() {
        List<Module> modules;
        if (selectedCategory == null) {
            modules = new ArrayList<>();
            for (List<Module> list : categoryModules.values()) {
                modules.addAll(list);
            }
        } else {
            modules = new ArrayList<>(categoryModules.getOrDefault(selectedCategory, Collections.emptyList()));
        }
        return modules.stream()
                .sorted(Comparator.comparing(m -> m.getName().toLowerCase()))
                .collect(Collectors.toList());
    }

    private void saveCurrentState() {
        lastCategory = selectedCategory != null ? selectedCategory.name() : null;
        lastModule = selectedModule != null ? selectedModule.getName() : null;
        lastModuleScroll = moduleScroll;
        lastValueScroll = valueScroll;
    }

    private void updateAnimations() {
        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.max(0.0f, Math.min(MAX_DELTA_TIME, (currentTime - lastAnimationTime) / 1000.0f));
        lastAnimationTime = currentTime;

        float targetGuiAnimation = isGuiOpen ? 1.0f : 0.0f;
        guiOpenAnimation = Math.max(0.0f, Math.min(1.0f,
                (float) animate(targetGuiAnimation, guiOpenAnimation, deltaTime * ANIMATION_SPEED_GUI)));

        categoryLineAnimation.update();
    }

    private Color getGlobalColor() {
        return new Color(81, 149, 219);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, this.width, this.height, new Color(0, 0, 0, 100).getRGB());
        mc.fontRendererObj.drawStringWithShadow("Myau " + Myau.version, 4, this.height - 3 - mc.fontRendererObj.FONT_HEIGHT * 2, new Color(60, 162, 253).getRGB());
        mc.fontRendererObj.drawStringWithShadow("dev, PlutoCrystal_", 4, this.height - 3 - mc.fontRendererObj.FONT_HEIGHT, new Color(60, 162, 253).getRGB());

        updateAnimations();
        handle(mouseX, mouseY, -1, GuiEvent.DRAW);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        handle(mouseX, mouseY, mouseButton, GuiEvent.CLICK);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        handle(mouseX, mouseY, state, GuiEvent.RELEASE);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (waitingForKey) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                selectedModule.setKey(0);
            } else if (keyCode == 11) {
                if (selectedModule instanceof GuiModule) {
                    selectedModule.setKey(54);
                } else {
                    selectedModule.setKey(0);
                }
            } else {
                selectedModule.setKey(keyCode);
            }
            waitingForKey = false;
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }

        for (GuiTextField tf : textFieldMap.values()) {
            tf.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void onGuiClosed() {
        lastPosX = this.posX;
        lastPosY = this.posY;
        saveCurrentState();
        savePositions();
        super.onGuiClosed();
        
        GuiModule guiModule = (GuiModule) Myau.moduleManager.modules.get(GuiModule.class);
        if (guiModule != null && guiModule.isEnabled()) {
            guiModule.setEnabled(false);
        }
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    public void handle(int mouseX, int mouseY, int mouseButton, GuiEvent event) {
        if (event == GuiEvent.RELEASE) {
            this.dragging = false;
            this.draggingSlider = false;
            this.currentDraggingSlider = null;
            for (ColorPickerState state : colorPickerStates.values()) {
                state.draggingHue = false;
                state.draggingColor = false;
                state.draggingAlpha = false;
            }
        }

        if (!isGuiOpen) {
            return;
        }

        handleDragging(mouseX, mouseY, mouseButton, event);

        if (event == GuiEvent.DRAW) {
            renderMainBackground();
            renderHeader();
        }

        renderCategories(mouseX, mouseY, mouseButton, event);
        renderModuleList(mouseX, mouseY, mouseButton, event);
        renderValueSettings(mouseX, mouseY, mouseButton, event);

        if (event == GuiEvent.DRAW) {
            updateColorPickerDrag(mouseX, mouseY);
        }
    }

    private void handleDragging(int mouseX, int mouseY, int mouseButton, GuiEvent event) {
        if (event == GuiEvent.CLICK) {
            if (isHovered(mouseX, mouseY, posX, posY, WIDTH, HEADER_HEIGHT) && mouseButton == 0) {
                dragging = true;
                draggingX = mouseX - posX;
                draggingY = mouseY - posY;
            }
        }

        if (event == GuiEvent.DRAW && dragging) {
            if (Mouse.isButtonDown(0)) {
                posX = mouseX - draggingX;
                posY = mouseY - draggingY;
            } else {
                dragging = false;
            }
        }
    }

    private void renderMainBackground() {
        drawRect(posX, posY, WIDTH, HEIGHT, COLOR_BACKGROUND.getRGB());
    }

    private void renderHeader() {
        drawRect(posX, posY, WIDTH, HEADER_HEIGHT, COLOR_HEADER.getRGB());
        mc.fontRendererObj.drawStringWithShadow("CLICKGUI", posX + 5.0f, posY + 6.0f, COLOR_TEXT_PRIMARY.getRGB());

        drawRect(posX + CATEGORY_OFFSET_X, posY + 0.5f, 2.0f, HEIGHT, COLOR_SEPARATOR.getRGB());
        drawRect(posX + CATEGORY_OFFSET_X, posY + 40.0f, WIDTH - CATEGORY_OFFSET_X + 0.5f, 2.0f, COLOR_SEPARATOR.getRGB());
    }

    private void renderCategories(int mouseX, int mouseY, int mouseButton, GuiEvent event) {
        float categoryX = posX + CATEGORY_OFFSET_X + 15.0f;
        float targetLineX = -1337.0f;

        for (Category category : Category.values()) {
            float categoryWidth = mc.fontRendererObj.getStringWidth(category.getDisplayName());
            float categoryHeight = mc.fontRendererObj.FONT_HEIGHT;

            if (event == GuiEvent.DRAW) {
                boolean isSelected = category == this.selectedCategory;
                boolean isHovered = isHovered(mouseX, mouseY, categoryX, posY + 20.0f, categoryWidth, categoryHeight);

                int textColor;
                if (isSelected) {
                    textColor = getGlobalColor().getRGB();
                } else if (isHovered) {
                    textColor = COLOR_TEXT_HOVER.getRGB();
                } else {
                    textColor = COLOR_TEXT_SECONDARY.getRGB();
                }

                mc.fontRendererObj.drawStringWithShadow(category.getDisplayName(), categoryX, posY + 25.0f, textColor);

                if (isSelected) {
                    targetLineX = categoryX;
                }
            } else if (event == GuiEvent.CLICK) {
                if (isHovered(mouseX, mouseY, categoryX, posY + 20.0f, categoryWidth, categoryHeight)) {
                    if (selectedCategory == category) {
                        selectedCategory = null;
                    } else {
                        selectedCategory = category;
                    }
                    selectedModule = null;
                    moduleScroll = 0.0f;
                    valueScroll = 0.0f;
                    colorPickerStates.clear();
                    saveCurrentState();
                }
            }
            categoryX += categoryWidth + 15.0f;
        }

        if (event == GuiEvent.DRAW && selectedCategory != null) {
            if (targetLineX != -1337.0f) {
                if (lastCategoryLineTargetX == -1337.0f) {
                    categoryLineAnimation.start(targetLineX, targetLineX);
                } else if (lastCategoryLineTargetX != targetLineX) {
                    categoryLineAnimation.start(categoryLineAnimation.getValue(), targetLineX);
                }
                lastCategoryLineTargetX = targetLineX;

                float currentLineX = (float) categoryLineAnimation.getValue();
                float lineWidth = mc.fontRendererObj.getStringWidth(selectedCategory.getDisplayName()) - 0.5f;
                float lineY = posY + 25.0f + mc.fontRendererObj.FONT_HEIGHT + 2.0f;
                drawRect(currentLineX, lineY, lineWidth, 2.0f, getGlobalColor().getRGB());
            }
        }
    }

    private void renderModuleList(int mouseX, int mouseY, int mouseButton, GuiEvent event) {
        if (event == GuiEvent.DRAW) {
            scissorStart(posX, posY + 16.5f, MODULE_LIST_WIDTH, HEIGHT - 16.5f);
        }

        if (isHovered(mouseX, mouseY, posX, posY + 16.5f, MODULE_LIST_WIDTH, HEIGHT - 16.5f)
                && event == GuiEvent.DRAW) {
            moduleScroll = Math.min(0.0f, moduleScroll + Mouse.getDWheel() * SCROLL_SPEED);
        }

        float moduleY = posY + 25.0f + moduleScroll;

        List<Module> modules = getCurrentModuleList();

        for (Module module : modules) {
            float moduleHeight = mc.fontRendererObj.FONT_HEIGHT;

            if (event == GuiEvent.DRAW) {
                float drawX = posX + 8.0f;
                if (module == selectedModule) {
                    int arrowColor = module.isEnabled() ? getGlobalColor().getRGB() : COLOR_TEXT_PRIMARY.getRGB();
                    mc.fontRendererObj.drawStringWithShadow(">", drawX, moduleY, arrowColor);
                    drawX += mc.fontRendererObj.getStringWidth("> ") + 2.0f;
                }

                int nameColor = module.isEnabled() ? getGlobalColor().getRGB() : COLOR_TEXT_PRIMARY.getRGB();
                mc.fontRendererObj.drawStringWithShadow(module.getName(), drawX, moduleY, nameColor);
            } else if (event == GuiEvent.CLICK) {
                float nameWidth = mc.fontRendererObj.getStringWidth(module.getName());
                if (isHovered(mouseX, mouseY, posX + 8.0f, moduleY, nameWidth, moduleHeight)) {
                    if (mouseButton == 0) {
                        module.toggle();
                    } else if (mouseButton == 1) {
                        selectedModule = module;
                        valueScroll = 0.0f;
                        colorPickerStates.clear();
                        saveCurrentState();
                    }
                }
            }
            moduleY += mc.fontRendererObj.FONT_HEIGHT + 4.0f;
        }

        if (event == GuiEvent.DRAW) {
            scissorEnd();
        }
    }

    private void renderValueSettings(int mouseX, int mouseY, int mouseButton, GuiEvent event) {
        if (selectedModule == null) {
            return;
        }

        float initialValueY = posY + 40.0f;
        float currentY = initialValueY + 8.0f;

        if (event == GuiEvent.DRAW) {
            mc.fontRendererObj.drawStringWithShadow(selectedModule.getName() + ":",
                    posX + VALUE_AREA_OFFSET_X, currentY, COLOR_TEXT_PRIMARY.getRGB());
        }

        currentY += mc.fontRendererObj.FONT_HEIGHT + 3.0f;

        renderKeyAndHideSettings(mouseX, mouseY, mouseButton, event, currentY);

        float headerHeight = (initialValueY + 8.0f) - (posY + 40.0f)
                + mc.fontRendererObj.FONT_HEIGHT + 3.0f
                + mc.fontRendererObj.FONT_HEIGHT + 10.0f;

        if (isHovered(mouseX, mouseY, posX + CATEGORY_OFFSET_X + 1.5f,
                initialValueY + 1.5f + 1.0f, WIDTH - (CATEGORY_OFFSET_X + 1.5f),
                HEIGHT - (40.0f + 1.5f)) && event == GuiEvent.DRAW) {
            float scrollDelta = Mouse.getDWheel() * SCROLL_SPEED;
            if (scrollDelta != 0) {
                valueScroll = Math.min(0.0f, valueScroll + scrollDelta);
                saveCurrentState();
            }
        }

        if (event == GuiEvent.DRAW) {
            scissorStart(posX + CATEGORY_OFFSET_X + 1.5f + 0.5f,
                    posY + 30.0f + headerHeight + 1.5f + 1.0f,
                    WIDTH - (CATEGORY_OFFSET_X + 1.5f),
                    HEIGHT - (31.0f + headerHeight + 1.5f));
        }

        currentY = initialValueY - 4.0f + headerHeight + valueScroll;

        List<Property<?>> properties = Myau.propertyManager.properties.get(selectedModule.getClass());
        if (properties != null) {
            for (Property<?> value : properties) {
                if (!value.isVisible()) {
                    continue;
                }
                currentY = renderProperty(mouseX, mouseY, mouseButton, event, currentY, value);
            }
        }

        if (event == GuiEvent.DRAW) {
            scissorEnd();
        }
    }

    private void renderKeyAndHideSettings(int mouseX, int mouseY, int mouseButton,
                                          GuiEvent event, float currentY) {
        String keyName = selectedModule.getKey() == 0 ? "None" : Keyboard.getKeyName(selectedModule.getKey());
        float keyWidth = mc.fontRendererObj.getStringWidth("Key: " + keyName);
        boolean isModuleHidden = selectedModule.isHidden();
        float hideWidth = mc.fontRendererObj.getStringWidth("Hide: " + isModuleHidden);

        boolean isKeyHovered = isHovered(mouseX, mouseY,
                posX + VALUE_AREA_OFFSET_X, currentY + 1.0f, keyWidth, mc.fontRendererObj.FONT_HEIGHT);
        boolean isHideHovered = isHovered(mouseX, mouseY,
                posX + 170.0f, currentY + 1.0f, hideWidth, mc.fontRendererObj.FONT_HEIGHT);

        if (event == GuiEvent.DRAW) {
            int hideTextColor = new Color(150, 150, 150).getRGB();
            int stateColor = isModuleHidden ? COLOR_ENABLED.getRGB() : COLOR_DISABLED.getRGB();

            if (waitingForKey) {
                mc.fontRendererObj.drawStringWithShadow("Key: ...",
                        posX + VALUE_AREA_OFFSET_X, currentY + 1.0f, getGlobalColor().getRGB());
            } else {
                mc.fontRendererObj.drawStringWithShadow("Key: " + keyName,
                        posX + VALUE_AREA_OFFSET_X, currentY + 1.0f, hideTextColor);
            }

            mc.fontRendererObj.drawStringWithShadow("Hide: ",
                    posX + 170.0f, currentY + 1.0f, hideTextColor);
            mc.fontRendererObj.drawStringWithShadow(String.valueOf(isModuleHidden),
                    posX + 170.0f + mc.fontRendererObj.getStringWidth("Hide: "),
                    currentY + 1.0f, stateColor);
        } else if (event == GuiEvent.CLICK) {
            if (isHideHovered && mouseButton == 0) {
                selectedModule.setHidden(!isModuleHidden);
            }
            if (isKeyHovered && mouseButton == 0) {
                waitingForKey = !waitingForKey;
            }
        }
    }

    private float renderProperty(int mouseX, int mouseY, int mouseButton,
                                 GuiEvent event, float currentY, Property<?> property) {
        if (property instanceof BooleanProperty) {
            return renderBoolValue(mouseX, mouseY, mouseButton, event, currentY, (BooleanProperty) property);
        } else if (property instanceof TextProperty) {
            return renderTextValue(mouseX, mouseY, mouseButton, event, currentY, (TextProperty) property);
        } else if (property instanceof FloatProperty || property instanceof IntProperty || property instanceof PercentProperty) {
            return renderNumValue(mouseX, mouseY, mouseButton, event, currentY, property);
        } else if (property instanceof ModeProperty) {
            return renderModeValue(mouseX, mouseY, mouseButton, event, currentY, (ModeProperty) property);
        } else if (property instanceof ColorProperty) {
            return renderColorValue(mouseX, mouseY, mouseButton, event, currentY, (ColorProperty) property);
        }
        return currentY;
    }

    private float renderBoolValue(int mouseX, int mouseY, int mouseButton,
                                  GuiEvent event, float currentY, BooleanProperty boolValue) {
        String valueText = boolValue.getValue() ? "true" : "false";
        float fullWidth = mc.fontRendererObj.getStringWidth(boolValue.getName() + ": " + valueText);

        if (event == GuiEvent.DRAW) {
            mc.fontRendererObj.drawStringWithShadow(boolValue.getName() + ": ",
                    posX + VALUE_AREA_OFFSET_X, currentY, COLOR_TEXT_PRIMARY.getRGB());
            int valueColor = boolValue.getValue() ? COLOR_ENABLED.getRGB() : COLOR_DISABLED.getRGB();
            mc.fontRendererObj.drawStringWithShadow(valueText,
                    posX + VALUE_AREA_OFFSET_X + mc.fontRendererObj.getStringWidth(boolValue.getName() + ": "),
                    currentY, valueColor);
        } else if (event == GuiEvent.CLICK) {
            if (isHovered(mouseX, mouseY, posX + VALUE_AREA_OFFSET_X, currentY,
                    fullWidth, mc.fontRendererObj.FONT_HEIGHT)) {
                boolValue.setValue(!boolValue.getValue());
            }
        }
        return currentY + mc.fontRendererObj.FONT_HEIGHT + 4.0f;
    }

    private float renderTextValue(int mouseX, int mouseY, int mouseButton,
                                  GuiEvent event, float currentY, TextProperty textValue) {
        float textFieldX = posX + VALUE_AREA_OFFSET_X
                + mc.fontRendererObj.getStringWidth(textValue.getName() + ": ");
        float textFieldY = currentY - 2.5f;
        float textFieldHeight = mc.fontRendererObj.FONT_HEIGHT + 2.0f;

        GuiTextField textField = textFieldMap.computeIfAbsent(textValue, k -> {
            GuiTextField tf = new GuiTextField(0, mc.fontRendererObj, (int) textFieldX, (int) textFieldY, (int) TEXT_FIELD_WIDTH, (int) textFieldHeight);
            tf.setText(textValue.getValue());
            tf.setEnableBackgroundDrawing(false);
            return tf;
        });

        if (event == GuiEvent.DRAW) {
            mc.fontRendererObj.drawStringWithShadow(textValue.getName() + ": ",
                    posX + VALUE_AREA_OFFSET_X, currentY, COLOR_TEXT_PRIMARY.getRGB());

            drawRect(textFieldX - 1.0f, textFieldY,
                    TEXT_FIELD_WIDTH + 2.0f, textFieldHeight, COLOR_TEXT_FIELD_BG.getRGB());
            drawRect(textFieldX, textFieldY + 1.0f,
                    TEXT_FIELD_WIDTH, textFieldHeight - 2.0f, COLOR_TEXT_FIELD_FG.getRGB());

            textField.xPosition = (int) (textFieldX + 4.0f);
            textField.yPosition = (int) (textFieldY + (textFieldHeight - mc.fontRendererObj.FONT_HEIGHT) / 2.0f + 1.0f);
            textField.width = (int) (TEXT_FIELD_WIDTH - 8.0f);
            textField.height = (int) textFieldHeight;
            textField.updateCursorCounter();

            String displayText = textField.getText();
            if (displayText.isEmpty() && !textField.isFocused()) {
                mc.fontRendererObj.drawStringWithShadow("Enter text...",
                        textFieldX + 4.0f,
                        textFieldY + (textFieldHeight - mc.fontRendererObj.FONT_HEIGHT) / 2.0f + 1.0f,
                        COLOR_TEXT_PLACEHOLDER.getRGB());
            } else {
                textField.drawTextBox();
            }
            textValue.setValue(textField.getText());
        } else if (event == GuiEvent.CLICK) {
            textField.xPosition = (int) textFieldX;
            textField.yPosition = (int) (textFieldY + (textFieldHeight - mc.fontRendererObj.FONT_HEIGHT) / 2.0f);
            textField.width = (int) (TEXT_FIELD_WIDTH - 8.0f);
            textField.height = (int) textFieldHeight;
            textField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        return currentY + mc.fontRendererObj.FONT_HEIGHT + 4.0f;
    }

    private float renderNumValue(int mouseX, int mouseY, int mouseButton,
                                 GuiEvent event, float currentY, Property<?> numValue) {
        String nameText = numValue.getName() + ": ";
        float nameWidth = mc.fontRendererObj.getStringWidth(nameText);
        float sliderX = posX + VALUE_AREA_OFFSET_X + nameWidth;
        float sliderY = currentY + (mc.fontRendererObj.FONT_HEIGHT - SLIDER_HEIGHT) / 2.0f;

        double min = 0, max = 100, increment = 1, currentVal = 0;
        if (numValue instanceof FloatProperty) {
            FloatProperty p = (FloatProperty) numValue;
            min = p.getMinimum(); max = p.getMaximum(); increment = 0.1; currentVal = p.getValue();
        } else if (numValue instanceof IntProperty) {
            IntProperty p = (IntProperty) numValue;
            min = p.getMinimum(); max = p.getMaximum(); increment = 1; currentVal = p.getValue();
        } else if (numValue instanceof PercentProperty) {
            PercentProperty p = (PercentProperty) numValue;
            min = p.getMinimum(); max = p.getMaximum(); increment = 1; currentVal = p.getValue();
        }

        if (event == GuiEvent.DRAW) {
            mc.fontRendererObj.drawStringWithShadow(nameText,
                    posX + VALUE_AREA_OFFSET_X, currentY, COLOR_TEXT_PRIMARY.getRGB());

            double targetLength = Math.max(0.0, Math.min(SLIDER_WIDTH,
                    (currentVal - min) / (max - min) * SLIDER_WIDTH));
            double currentLength = numberSettingMap.getOrDefault(numValue, (float) targetLength);

            if (draggingSlider && currentDraggingSlider == numValue) {
                currentLength = targetLength;
            } else {
                currentLength = animate(targetLength, currentLength, 0.2);
            }

            numberSettingMap.put(numValue, (float) currentLength);

            drawRect(sliderX, sliderY, SLIDER_WIDTH,
                    SLIDER_HEIGHT, COLOR_SLIDER_BG.getRGB());
            drawRect(sliderX, sliderY,
                    (float) currentLength, SLIDER_HEIGHT, getGlobalColor().getRGB());

            String valueStr = String.valueOf(Math.round(currentVal * 100.0) / 100.0);
            if (numValue instanceof IntProperty || numValue instanceof PercentProperty) {
                valueStr = String.valueOf((int) currentVal);
            }
            float valueStrWidth = mc.fontRendererObj.getStringWidth(valueStr);
            float textX = sliderX + SLIDER_WIDTH / 2.0f - valueStrWidth / 2.0f;
            float textY = sliderY + SLIDER_HEIGHT / 2.0f - mc.fontRendererObj.FONT_HEIGHT / 2.0f;

            mc.fontRendererObj.drawStringWithShadow(valueStr, textX, textY, COLOR_TEXT_PRIMARY.getRGB());

            if (draggingSlider && currentDraggingSlider == numValue && Mouse.isButtonDown(0)) {
                updateSliderValue(mouseX, sliderX, SLIDER_WIDTH, min, max, increment, numValue);
            }
        } else if (event == GuiEvent.CLICK) {
            if (mouseButton == 0 && isHovered(mouseX, mouseY, sliderX,
                    sliderY - 2.0f, SLIDER_WIDTH, SLIDER_HEIGHT + 4.0f)) {
                draggingSlider = true;
                currentDraggingSlider = numValue;
                updateSliderValue(mouseX, sliderX, SLIDER_WIDTH, min, max, increment, numValue);
            }
        }
        return currentY + Math.max(mc.fontRendererObj.FONT_HEIGHT, SLIDER_HEIGHT) + 4.0f;
    }

    private float renderModeValue(int mouseX, int mouseY, int mouseButton,
                                  GuiEvent event, float currentY, ModeProperty modeValue) {
        float modeX = posX + VALUE_AREA_OFFSET_X
                + mc.fontRendererObj.getStringWidth(modeValue.getName() + ": ");
        float tempY = currentY;

        String currentMode = modeValue.getModeString().replace("_", " ");

        if (event == GuiEvent.DRAW) {
            mc.fontRendererObj.drawStringWithShadow(modeValue.getName() + ": ",
                    posX + VALUE_AREA_OFFSET_X, currentY, COLOR_TEXT_PRIMARY.getRGB());
            mc.fontRendererObj.drawStringWithShadow(currentMode, modeX, tempY, getGlobalColor().getRGB());
        } else if (event == GuiEvent.CLICK) {
            float modeWidth = mc.fontRendererObj.getStringWidth(currentMode);
            if (isHovered(mouseX, mouseY, modeX, tempY,
                    modeWidth, mc.fontRendererObj.FONT_HEIGHT)) {
                if (mouseButton == 0) {
                    modeValue.nextMode();
                } else if (mouseButton == 1) {
                    modeValue.previousMode();
                }
            }
        }
        return tempY + mc.fontRendererObj.FONT_HEIGHT + 4.0f;
    }

    private float renderColorValue(int mouseX, int mouseY, int mouseButton,
                                   GuiEvent event, float currentY, ColorProperty colorValue) {
        float pickerX = posX + VALUE_AREA_OFFSET_X;
        ColorPickerState state = getColorState(colorValue);

        state.pickerX = pickerX;
        state.pickerY = currentY + mc.fontRendererObj.FONT_HEIGHT;
        state.hueSliderY = state.pickerY + COLOR_PICKER_HEIGHT + 5.0f;
        state.alphaSliderY = state.hueSliderY + HUE_SLIDER_HEIGHT + 5.0f;

        if (event == GuiEvent.DRAW) {
            mc.fontRendererObj.drawStringWithShadow(colorValue.getName() + ": ",
                    posX + VALUE_AREA_OFFSET_X, currentY, COLOR_TEXT_PRIMARY.getRGB());
        }

        if (event == GuiEvent.DRAW) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);

            Color hueColor = Color.getHSBColor(state.hue, 1.0f, 1.0f);
            drawRect(state.pickerX, state.pickerY,
                    COLOR_PICKER_WIDTH, COLOR_PICKER_HEIGHT, hueColor.getRGB());

            renderColorPickerGradients(state);
            renderHueSlider(state, colorValue);
            renderAlphaSlider(state, colorValue);
            renderColorPreview(state, colorValue);
            renderColorIndicators(state, colorValue);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_BLEND);
        } else if (event == GuiEvent.CLICK) {
            handleColorPickerClick(mouseX, mouseY, mouseButton, state, colorValue);
        }

        return currentY + mc.fontRendererObj.FONT_HEIGHT + 4.0f
                + COLOR_PICKER_HEIGHT + 5.0f
                + HUE_SLIDER_HEIGHT + 5.0f
                + ALPHA_SLIDER_HEIGHT + 4.0f;
    }

    private ColorPickerState getColorState(ColorProperty prop) {
        return colorPickerStates.computeIfAbsent(prop, k -> {
            ColorPickerState s = new ColorPickerState();
            Color c = new Color(prop.getValue(), true);
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            s.hue = hsb[0];
            s.saturation = hsb[1];
            s.brightness = hsb[2];
            s.alpha = c.getAlpha() / 255.0f;
            return s;
        });
    }

    private void renderColorPickerGradients(ColorPickerState state) {
        for (int x = 0; x < COLOR_PICKER_WIDTH; x++) {
            float saturation = x / COLOR_PICKER_WIDTH;
            Color whiteGradient = new Color(255, 255, 255, (int) (255 * (1.0f - saturation)));
            drawRect(state.pickerX + x, state.pickerY, 1.0f, COLOR_PICKER_HEIGHT,
                    whiteGradient.getRGB());
        }

        for (int y = 0; y < COLOR_PICKER_HEIGHT; y++) {
            float brightness = 1.0f - (y / COLOR_PICKER_HEIGHT);
            Color blackGradient = new Color(0, 0, 0, (int) (255 * (1.0f - brightness)));
            drawRect(state.pickerX, state.pickerY + y, COLOR_PICKER_WIDTH, 1.0f,
                    blackGradient.getRGB());
        }
    }

    private void renderHueSlider(ColorPickerState state, ColorProperty colorValue) {
        drawRect(state.pickerX, state.hueSliderY,
                COLOR_PICKER_WIDTH, HUE_SLIDER_HEIGHT, COLOR_INDICATOR_BLACK.getRGB());

        for (float x = 0; x < COLOR_PICKER_WIDTH; x++) {
            float hue = x / COLOR_PICKER_WIDTH;
            Color c = Color.getHSBColor(hue, 1.0f, 1.0f);
            drawRect(state.pickerX + x, state.hueSliderY + 1.0f,
                    1.0f, HUE_SLIDER_HEIGHT - 2.0f, c.getRGB());
        }

        float huePos = state.pickerX + (state.hue * COLOR_PICKER_WIDTH);
        drawRect(huePos - 2.0f, state.hueSliderY - 2.0f,
                4.0f, HUE_SLIDER_HEIGHT + 4.0f, COLOR_INDICATOR_WHITE.getRGB());
        drawRect(huePos - 1.0f, state.hueSliderY,
                2.0f, HUE_SLIDER_HEIGHT, COLOR_INDICATOR_BLACK.getRGB());
    }

    private void renderAlphaSlider(ColorPickerState state, ColorProperty colorValue) {
        drawRect(state.pickerX, state.alphaSliderY,
                COLOR_PICKER_WIDTH, ALPHA_SLIDER_HEIGHT, COLOR_INDICATOR_WHITE.getRGB());

        int rgb = Color.HSBtoRGB(state.hue, state.saturation, state.brightness);
        Color currentColor = new Color(rgb);

        for (float x = 0; x < COLOR_PICKER_WIDTH; x++) {
            float alpha = x / COLOR_PICKER_WIDTH;
            Color c = new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), (int)(alpha * 255));
            drawRect(state.pickerX + x, state.alphaSliderY + 1.0f,
                    1.0f, ALPHA_SLIDER_HEIGHT - 2.0f, c.getRGB());
        }

        float alphaPos = state.pickerX + (state.alpha * COLOR_PICKER_WIDTH);
        drawRect(alphaPos - 2.0f, state.alphaSliderY - 2.0f,
                4.0f, ALPHA_SLIDER_HEIGHT + 4.0f, COLOR_INDICATOR_WHITE.getRGB());
        drawRect(alphaPos - 1.0f, state.alphaSliderY,
                2.0f, ALPHA_SLIDER_HEIGHT, COLOR_INDICATOR_BLACK.getRGB());
    }

    private void renderColorPreview(ColorPickerState state, ColorProperty colorValue) {
        float previewX = state.pickerX + COLOR_PICKER_WIDTH + 5.0f;
        float previewY = state.pickerY;

        drawRect(previewX, previewY, COLOR_PREVIEW_SIZE, COLOR_PREVIEW_SIZE, COLOR_INDICATOR_WHITE.getRGB());
        drawRect(previewX + 1, previewY + 1, COLOR_PREVIEW_SIZE - 2, COLOR_PREVIEW_SIZE - 2, COLOR_INDICATOR_BLACK.getRGB());
        int rgb = Color.HSBtoRGB(state.hue, state.saturation, state.brightness);
        Color c = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, (int)(state.alpha * 255));
        drawRect(previewX + 1, previewY + 1, COLOR_PREVIEW_SIZE - 2, COLOR_PREVIEW_SIZE - 2, c.getRGB());
    }

    private void renderColorIndicators(ColorPickerState state, ColorProperty colorValue) {
        float colorPosX = state.pickerX + (state.saturation * COLOR_PICKER_WIDTH);
        float colorPosY = state.pickerY + ((1.0f - state.brightness) * COLOR_PICKER_HEIGHT);

        drawRect(colorPosX - 3.0f, colorPosY - 3.0f,
                6.0f, 6.0f, COLOR_INDICATOR_WHITE.getRGB());
        drawRect(colorPosX - 2.0f, colorPosY - 2.0f,
                4.0f, 4.0f, COLOR_INDICATOR_BLACK.getRGB());
    }

    private void handleColorPickerClick(int mouseX, int mouseY, int mouseButton,
                                        ColorPickerState state, ColorProperty colorValue) {
        if (mouseButton == 0) {
            if (isHovered(mouseX, mouseY, state.pickerX, state.hueSliderY,
                    COLOR_PICKER_WIDTH, HUE_SLIDER_HEIGHT)) {
                state.draggingHue = true;
                state.hue = MathHelper.clamp_float((mouseX - state.pickerX) / COLOR_PICKER_WIDTH, 0.0f, 1.0f);
                updateColorProperty(state, colorValue);
            } else if (isHovered(mouseX, mouseY, state.pickerX, state.alphaSliderY,
                    COLOR_PICKER_WIDTH, ALPHA_SLIDER_HEIGHT)) {
                state.draggingAlpha = true;
                state.alpha = MathHelper.clamp_float((mouseX - state.pickerX) / COLOR_PICKER_WIDTH, 0.0f, 1.0f);
                updateColorProperty(state, colorValue);
            } else if (isHovered(mouseX, mouseY, state.pickerX, state.pickerY,
                    COLOR_PICKER_WIDTH, COLOR_PICKER_HEIGHT)) {
                state.draggingColor = true;
                state.saturation = MathHelper.clamp_float((mouseX - state.pickerX) / COLOR_PICKER_WIDTH, 0.0f, 1.0f);
                state.brightness = 1.0f - MathHelper.clamp_float((mouseY - state.pickerY) / COLOR_PICKER_HEIGHT, 0.0f, 1.0f);
                updateColorProperty(state, colorValue);
            }
        }
    }

    private void updateColorPickerDrag(int mouseX, int mouseY) {
        for (Map.Entry<ColorProperty, ColorPickerState> entry : colorPickerStates.entrySet()) {
            ColorProperty colorValue = entry.getKey();
            ColorPickerState state = entry.getValue();

            if (state.draggingHue) {
                state.hue = MathHelper.clamp_float((mouseX - state.pickerX) / COLOR_PICKER_WIDTH, 0.0f, 1.0f);
                updateColorProperty(state, colorValue);
            } else if (state.draggingAlpha) {
                state.alpha = MathHelper.clamp_float((mouseX - state.pickerX) / COLOR_PICKER_WIDTH, 0.0f, 1.0f);
                updateColorProperty(state, colorValue);
            } else if (state.draggingColor) {
                state.saturation = MathHelper.clamp_float((mouseX - state.pickerX) / COLOR_PICKER_WIDTH, 0.0f, 1.0f);
                state.brightness = 1.0f - MathHelper.clamp_float((mouseY - state.pickerY) / COLOR_PICKER_HEIGHT, 0.0f, 1.0f);
                updateColorProperty(state, colorValue);
            }
        }
    }

    private void updateColorProperty(ColorPickerState state, ColorProperty prop) {
        int rgb = Color.HSBtoRGB(state.hue, state.saturation, state.brightness);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = (int) (state.alpha * 255);
        int color = (a << 24) | (r << 16) | (g << 8) | b;
        prop.setValue(color);
    }

    private void updateSliderValue(int mouseX, float sliderX, float sliderWidth,
                                   double min, double max, double increment, Property<?> prop) {
        double rawValue = (mouseX - sliderX) * (max - min) / sliderWidth + min;
        double steppedValue = Math.round(rawValue / increment) * increment;
        double newValue = MathHelper.clamp_double(steppedValue, min, max);

        if (prop instanceof FloatProperty) {
            ((FloatProperty) prop).setValue((float) newValue);
        } else if (prop instanceof IntProperty) {
            ((IntProperty) prop).setValue((int) newValue);
        } else if (prop instanceof PercentProperty) {
            ((PercentProperty) prop).setValue((int) newValue);
        }
    }

    private void drawRect(float x, float y, float width, float height, int color) {
        net.minecraft.client.gui.Gui.drawRect((int) x, (int) y, (int) (x + width), (int) (y + height), color);
    }

    private boolean isHovered(int mouseX, int mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private void scissorStart(float x, float y, float width, float height) {
        ScaledResolution sr = new ScaledResolution(mc);
        double scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                (int) (x * scale),
                (int) ((sr.getScaledHeight() - y - height) * scale),
                (int) (width * scale),
                (int) (height * scale)
        );
    }

    private void scissorEnd() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private double animate(double target, double current, double speed) {
        double delta = target - current;
        if (Math.abs(delta) < 0.001) return target;
        return current + delta * speed;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void savePositions() {
        JsonObject json = new JsonObject();
        json.addProperty("x", posX);
        json.addProperty("y", posY);
        if (selectedCategory != null) {
            json.addProperty("category", selectedCategory.name());
        }
        if (selectedModule != null) {
            json.addProperty("module", selectedModule.getName());
        }
        json.addProperty("moduleScroll", moduleScroll);
        json.addProperty("valueScroll", valueScroll);
        try (FileWriter writer = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            if (json.has("x")) posX = json.get("x").getAsFloat();
            if (json.has("y")) posY = json.get("y").getAsFloat();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}