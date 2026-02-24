package zx.offical.quattro.authenticator.listener;

import zx.offical.quattro.authenticator.accounts.MinecraftAccount;

public interface LoginListener{
    void onLoginDone(MinecraftAccount account);
    void onLoginError(Throwable errorMessage);
    void onLoginProgress(int step);
    void setMaxLoginProgress(int max);
}
