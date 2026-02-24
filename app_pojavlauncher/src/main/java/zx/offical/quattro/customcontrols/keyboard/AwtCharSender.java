package zx.offical.quattro.customcontrols.keyboard;

import zx.offical.quattro.AWTInputBridge;
import zx.offical.quattro.AWTInputEvent;

/** Send chars via the AWT Bridgee */
public class AwtCharSender implements CharacterSenderStrategy {
    @Override
    public void sendBackspace() {
        AWTInputBridge.sendKey(' ', AWTInputEvent.VK_BACK_SPACE);
    }

    @Override
    public void sendEnter() {
        AWTInputBridge.sendKey(' ', AWTInputEvent.VK_ENTER);
    }

    @Override
    public void sendChar(char character) {
        AWTInputBridge.sendChar(character);
    }

}
