package zx.offical.quattro.authenticator;

import androidx.annotation.NonNull;

import zx.offical.quattro.authenticator.listener.LoginListener;
import zx.offical.quattro.authenticator.accounts.MinecraftAccount;

public interface BackgroundLogin {
    void createAccount(@NonNull LoginListener loginListener, String code);
    void refreshAccount(@NonNull LoginListener loginListener, MinecraftAccount account);
    interface Creator {
        BackgroundLogin create();
    }
}
