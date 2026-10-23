package com.example.authmod.client;

import com.example.authmod.network.C2SChangePasswordPacket;
import com.example.authmod.network.C2SLoginPacket;
import com.example.authmod.network.C2SRegisterPacket;
import com.example.authmod.network.ModNetwork;
import com.example.authmod.network.S2CAuthResultPacket;
import com.example.authmod.network.S2COpenAuthPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class AuthScreen extends Screen {
    private final Mode mode;
    private final String username;
    private final int maxAttempts;
    private final List<EditBox> fields = new ArrayList<>();
    private EditBox passwordField;
    private EditBox oldPasswordField;
    private EditBox newPasswordField;
    private EditBox confirmField;
    private Button submitButton;
    private Component feedback;
    private boolean feedbackIsError;
    private boolean processing;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    public AuthScreen(S2COpenAuthPacket packet) {
        super(Component.translatable(Mode.fromId(packet.mode()).titleKey));
        this.mode = Mode.fromId(packet.mode());
        this.username = packet.username();
        this.maxAttempts = packet.maxAttempts();
    }

    @Override
    protected void init() {
        fields.clear();
        int fieldCount = mode == Mode.LOGIN ? 1 : mode == Mode.CHANGE_PASSWORD ? 3 : 2;
        panelHeight = mode == Mode.LOGIN ? 174 : mode == Mode.REGISTER ? 208 : 242;
        panelLeft = (width - 260) / 2;
        panelTop = (height - panelHeight) / 2;
        int fieldX = panelLeft + 18;
        int fieldWidth = 224;
        int startY = panelTop + 58;

        if (mode == Mode.LOGIN) {
            passwordField = createPasswordField(fieldX, startY, fieldWidth,
                "authmod.gui.field.password");
        } else if (mode == Mode.REGISTER) {
            passwordField = createPasswordField(fieldX, startY, fieldWidth,
                "authmod.gui.field.password");
            confirmField = createPasswordField(fieldX, startY + 36, fieldWidth,
                "authmod.gui.field.confirm");
        } else {
            oldPasswordField = createPasswordField(fieldX, startY, fieldWidth,
                "authmod.gui.field.old");
            newPasswordField = createPasswordField(fieldX, startY + 36, fieldWidth,
                "authmod.gui.field.password");
            confirmField = createPasswordField(fieldX, startY + 72, fieldWidth,
                "authmod.gui.field.confirm");
        }

        EditBox firstField = fields.getFirst();
        setInitialFocus(firstField);
        firstField.setFocused(true);

        int buttonY = panelTop + panelHeight - 34;
        int buttonWidth = mode == Mode.CHANGE_PASSWORD ? 104 : 140;
        int submitX = mode == Mode.CHANGE_PASSWORD ? panelLeft + 18 : panelLeft + (260 - buttonWidth) / 2;
        submitButton = addRenderableWidget(Button.builder(Component.translatable(mode.buttonKey), button -> submit())
            .bounds(submitX, buttonY, buttonWidth, 20)
            .build());
        if (mode == Mode.CHANGE_PASSWORD) {
            addRenderableWidget(Button.builder(Component.translatable("authmod.gui.button.cancel"), button -> onClose())
                .bounds(panelLeft + 138, buttonY, 104, 20)
                .build());
        }
        updateSubmitState();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft, panelTop, panelLeft + 260, panelTop + panelHeight, 0xC8000000);
        graphics.fill(panelLeft, panelTop, panelLeft + 260, panelTop + 1, 0xFFE0E0E0);
        graphics.fill(panelLeft, panelTop + panelHeight - 1, panelLeft + 260, panelTop + panelHeight, 0xFF606060);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 12, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("authmod.gui.hint.user", username),
            panelLeft + 18, panelTop + 34, 0xFFD0D0D0);
        if (mode == Mode.LOGIN) {
            graphics.drawString(font, Component.translatable("authmod.gui.hint.attempts", maxAttempts),
                panelLeft + 18, panelTop + 51, 0xFFB0B0B0);
        }
        for (int i = 0; i < fields.size(); i++) {
            graphics.drawString(font, Component.translatable(fieldLabelKey(i)),
                panelLeft + 18, fields.get(i).getY() - 10, 0xFFD0D0D0);
        }
        if (feedback != null) {
            graphics.drawCenteredString(font, feedback, width / 2, panelTop + panelHeight - 55,
                feedbackIsError ? 0xFFFF5555 : 0xFF55FF55);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB && fields.size() > 1) {
            cycleFieldFocus();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (submitButton != null && submitButton.active) {
                submit();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return mode == Mode.CHANGE_PASSWORD;
    }

    @Override
    public void onClose() {
        if (mode == Mode.CHANGE_PASSWORD) {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void applyResult(S2CAuthResultPacket packet) {
        if (packet.mode() != mode.id) {
            return;
        }
        processing = false;
        if (packet.success()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        feedback = Component.translatable(packet.messageKey(), packet.args().toArray());
        feedbackIsError = true;
        updateSubmitState();
    }

    private EditBox createPasswordField(int x, int y, int fieldWidth, String labelKey) {
        EditBox field = new EditBox(font, x, y + 12, fieldWidth, 18,
            Component.translatable(labelKey));
        field.setMaxLength(64);
        field.setFormatter((value, cursor) ->
            FormattedCharSequence.forward("•".repeat(value.length()), Style.EMPTY));
        field.setHint(Component.translatable(labelKey));
        field.setResponder(value -> {
            feedback = null;
            feedbackIsError = false;
            updateSubmitState();
        });
        fields.add(field);
        addRenderableWidget(field);
        return field;
    }

    private void cycleFieldFocus() {
        int focusedIndex = -1;
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).isFocused()) {
                focusedIndex = i;
                fields.get(i).setFocused(false);
                break;
            }
        }
        int next = (focusedIndex + 1) % fields.size();
        fields.get(next).setFocused(true);
    }

    private String fieldLabelKey(int index) {
        if (mode == Mode.LOGIN || (mode == Mode.REGISTER && index == 0)) {
            return "authmod.gui.field.password";
        }
        if (mode == Mode.CHANGE_PASSWORD) {
            return switch (index) {
                case 0 -> "authmod.gui.field.old";
                case 1 -> "authmod.gui.field.password";
                default -> "authmod.gui.field.confirm";
            };
        }
        return "authmod.gui.field.confirm";
    }

    private void submit() {
        if (processing || !validateFields()) {
            updateSubmitState();
            return;
        }
        processing = true;
        feedback = Component.translatable("authmod.gui.hint.processing");
        feedbackIsError = false;
        updateSubmitState();
        switch (mode) {
            case REGISTER -> ModNetwork.sendToServer(new C2SRegisterPacket(
                passwordField.getValue(), confirmField.getValue()));
            case LOGIN -> ModNetwork.sendToServer(new C2SLoginPacket(passwordField.getValue()));
            case CHANGE_PASSWORD -> ModNetwork.sendToServer(new C2SChangePasswordPacket(
                oldPasswordField.getValue(), newPasswordField.getValue(), confirmField.getValue()));
        }
    }

    private boolean validateFields() {
        List<EditBox> required = fields;
        for (EditBox field : required) {
            String value = field.getValue();
            if (value.isEmpty()) {
                setValidationError("authmod.gui.hint.empty");
                return false;
            }
            if (value.length() < 6 || value.length() > 64) {
                setValidationError("authmod.gui.hint.too_short");
                return false;
            }
            for (int i = 0; i < value.length(); i++) {
                if (Character.isWhitespace(value.charAt(i)) || Character.isISOControl(value.charAt(i))) {
                    setValidationError("authmod.gui.hint.invalid_char");
                    return false;
                }
            }
        }
        String first = mode == Mode.CHANGE_PASSWORD ? newPasswordField.getValue() : passwordField.getValue();
        if (mode != Mode.LOGIN && !first.equals(confirmField.getValue())) {
            setValidationError("authmod.gui.hint.mismatch");
            return false;
        }
        return true;
    }

    private void setValidationError(String key) {
        feedback = Component.translatable(key);
        feedbackIsError = true;
    }

    private void updateSubmitState() {
        if (submitButton == null) {
            return;
        }
        submitButton.active = !processing && locallyValid();
    }

    private boolean locallyValid() {
        if (fields.isEmpty()) {
            return false;
        }
        for (EditBox field : fields) {
            String value = field.getValue();
            if (value.length() < 6 || value.length() > 64) {
                return false;
            }
            for (int i = 0; i < value.length(); i++) {
                if (Character.isWhitespace(value.charAt(i)) || Character.isISOControl(value.charAt(i))) {
                    return false;
                }
            }
        }
        return mode == Mode.LOGIN || (mode == Mode.CHANGE_PASSWORD
            ? newPasswordField.getValue().equals(confirmField.getValue())
            : passwordField.getValue().equals(confirmField.getValue()));
    }

    private enum Mode {
        REGISTER(0, "authmod.gui.title.register", "authmod.gui.button.register"),
        LOGIN(1, "authmod.gui.title.login", "authmod.gui.button.login"),
        CHANGE_PASSWORD(2, "authmod.gui.title.change", "authmod.gui.button.confirm");

        private final int id;
        private final String titleKey;
        private final String buttonKey;

        Mode(int id, String titleKey, String buttonKey) {
            this.id = id;
            this.titleKey = titleKey;
            this.buttonKey = buttonKey;
        }

        private static Mode fromId(int id) {
            for (Mode mode : values()) {
                if (mode.id == id) {
                    return mode;
                }
            }
            return LOGIN;
        }
    }
}
