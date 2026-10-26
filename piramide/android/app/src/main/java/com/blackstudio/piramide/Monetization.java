package com.blackstudio.piramide;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Anuncios con recompensa (AdMob) y suscripción Premium (Google Play Billing). */
public class Monetization implements PurchasesUpdatedListener {
    private static final String SUB_ID = BuildConfig.SUB_PRODUCT_ID;
    private static final long AD_RETRY_MS = 30_000;

    private final MainActivity act;
    private final WebView web;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean adsStarted = new AtomicBoolean(false);

    private BillingClient billing;
    private boolean billingReady;
    private ProductDetails subDetails;
    private volatile boolean premium;
    private volatile String price = "";

    private ConsentInformation consent;
    private volatile RewardedAd rewarded;
    private boolean loadingAd;
    private boolean restoring;

    Monetization(MainActivity act, WebView web) {
        this.act = act;
        this.web = web;
        this.prefs = act.getSharedPreferences("piramide", 0);
        this.premium = prefs.getBoolean("premium", false);
    }

    void start() {
        setupBilling();
        setupConsentAndAds();
    }

    void destroy() {
        main.removeCallbacksAndMessages(null);
        if (billing != null) billing.endConnection();
    }

    // ---------- estado para el juego ----------

    boolean isPremium() {
        return premium;
    }

    String price() {
        return price;
    }

    boolean adReady() {
        return rewarded != null;
    }

    private void js(String code) {
        main.post(() -> web.evaluateJavascript("try{" + code + "}catch(e){}", null));
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private void setPremium(boolean value) {
        boolean changed = premium != value;
        premium = value;
        prefs.edit().putBoolean("premium", value).apply();
        if (changed) js("window.onPremium&&window.onPremium(" + value + ")");
    }

    // ---------- consentimiento + anuncios ----------

    private void setupConsentAndAds() {
        consent = UserMessagingPlatform.getConsentInformation(act);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(act, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(act, error -> {
                    if (consent.canRequestAds()) startAds();
                }),
                error -> {
                    if (consent.canRequestAds()) startAds();
                });
        if (consent.canRequestAds()) startAds();
    }

    boolean privacyOptionsRequired() {
        return consent != null && consent.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    void showPrivacyOptions() {
        main.post(() -> UserMessagingPlatform.showPrivacyOptionsForm(act, error -> {
        }));
    }

    private void startAds() {
        if (adsStarted.getAndSet(true)) return;
        new Thread(() -> MobileAds.initialize(act, status -> main.post(this::loadAd))).start();
    }

    private void loadAd() {
        if (premium || rewarded != null || loadingAd || act.isFinishing()) return;
        loadingAd = true;
        RewardedAd.load(act, BuildConfig.ADMOB_REWARDED_ID, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        rewarded = ad;
                        loadingAd = false;
                        js("window.onAdReady&&window.onAdReady(true)");
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        rewarded = null;
                        loadingAd = false;
                        main.postDelayed(Monetization.this::loadAd, AD_RETRY_MS);
                    }
                });
    }

    void showAd() {
        main.post(() -> {
            RewardedAd ad = rewarded;
            if (ad == null) {
                js("window.onAdResult&&window.onAdResult('noad')");
                loadAd();
                return;
            }
            rewarded = null;
            final boolean[] earned = {false};
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    js("window.onAdResult&&window.onAdResult('" + (earned[0] ? "ok" : "skip") + "')");
                    loadAd();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError error) {
                    js("window.onAdResult&&window.onAdResult('noad')");
                    loadAd();
                }
            });
            ad.show(act, reward -> earned[0] = true);
        });
    }

    // ---------- suscripción ----------

    private void setupBilling() {
        billing = BillingClient.newBuilder(act)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
        billing.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    billingReady = true;
                    queryProduct();
                    refreshPurchases();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                billingReady = false;
            }
        });
    }

    private void queryProduct() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SUB_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()))
                .build();
        billing.queryProductDetailsAsync(params, (result, details) -> {
            List<ProductDetails> list = details.getProductDetailsList();
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || list == null || list.isEmpty()) return;
            subDetails = list.get(0);
            ProductDetails.SubscriptionOfferDetails offer = baseOffer();
            if (offer != null) {
                List<ProductDetails.PricingPhase> phases = offer.getPricingPhases().getPricingPhaseList();
                price = phases.get(phases.size() - 1).getFormattedPrice();
                js("window.onPrice&&window.onPrice(" + quote(price) + ")");
            }
        });
    }

    /** El plan base (sin ofertas de prueba); si no existe, la primera oferta. */
    private ProductDetails.SubscriptionOfferDetails baseOffer() {
        if (subDetails == null) return null;
        List<ProductDetails.SubscriptionOfferDetails> offers = subDetails.getSubscriptionOfferDetails();
        if (offers == null || offers.isEmpty()) return null;
        for (ProductDetails.SubscriptionOfferDetails o : offers) {
            if (o.getOfferId() == null) return o;
        }
        return offers.get(0);
    }

    void subscribe() {
        main.post(() -> {
            ProductDetails.SubscriptionOfferDetails offer = baseOffer();
            if (!billingReady || offer == null) {
                js("window.onBuyResult&&window.onBuyResult('unavailable')");
                if (billingReady) queryProduct();
                return;
            }
            BillingFlowParams.ProductDetailsParams item = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(subDetails)
                    .setOfferToken(offer.getOfferToken())
                    .build();
            BillingResult r = billing.launchBillingFlow(act, BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(item))
                    .build());
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                js("window.onBuyResult&&window.onBuyResult('error')");
            }
        });
    }

    @Override
    public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases, true);
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            js("window.onBuyResult&&window.onBuyResult('cancel')");
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            restore();
        } else {
            js("window.onBuyResult&&window.onBuyResult('error')");
        }
    }

    private void handlePurchases(List<Purchase> purchases, boolean fromBuyFlow) {
        boolean active = false;
        boolean pending = false;
        for (Purchase p : purchases) {
            if (!p.getProducts().contains(SUB_ID)) continue;
            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                active = true;
                if (!p.isAcknowledged()) {
                    billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(p.getPurchaseToken()).build(), r -> {
                    });
                }
            } else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                pending = true;
            }
        }
        setPremium(active);
        if (fromBuyFlow) {
            if (active) js("window.onBuyResult&&window.onBuyResult('ok')");
            else if (pending) js("window.onBuyResult&&window.onBuyResult('pending')");
        }
    }

    void refreshPurchases() {
        if (!billingReady) return;
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS).build(),
                (result, list) -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        handlePurchases(list, false);
                    }
                    if (restoring) {
                        restoring = false;
                        js("window.onRestore&&window.onRestore(" + premium + ")");
                    }
                });
    }

    void restore() {
        main.post(() -> {
            if (!billingReady) {
                js("window.onRestore&&window.onRestore(" + premium + ")");
                return;
            }
            restoring = true;
            refreshPurchases();
        });
    }

    void manageSubscription() {
        main.post(() -> act.openExternal(Uri.parse("https://play.google.com/store/account/subscriptions?sku="
                + SUB_ID + "&package=" + act.getPackageName())));
    }
}
