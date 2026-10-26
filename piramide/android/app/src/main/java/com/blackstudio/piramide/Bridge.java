package com.blackstudio.piramide;

import android.webkit.JavascriptInterface;

/** Funciones que el juego (JavaScript) puede llamar como window.PiramideAndroid.*. */
public class Bridge {
    private final Monetization money;

    Bridge(Monetization money) {
        this.money = money;
    }

    @JavascriptInterface
    public boolean isPremium() {
        return money.isPremium();
    }

    @JavascriptInterface
    public String price() {
        return money.price();
    }

    @JavascriptInterface
    public boolean adReady() {
        return money.adReady();
    }

    @JavascriptInterface
    public void showAd() {
        money.showAd();
    }

    @JavascriptInterface
    public void subscribe() {
        money.subscribe();
    }

    @JavascriptInterface
    public void restore() {
        money.restore();
    }

    @JavascriptInterface
    public void manage() {
        money.manageSubscription();
    }

    @JavascriptInterface
    public boolean privacyRequired() {
        return money.privacyOptionsRequired();
    }

    @JavascriptInterface
    public void privacy() {
        money.showPrivacyOptions();
    }
}
