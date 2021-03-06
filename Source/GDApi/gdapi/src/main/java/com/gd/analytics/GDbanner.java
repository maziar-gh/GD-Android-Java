package com.gd.analytics;

import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

class GDbanner {

    private static Thread adBannerTimer, adInterstitialTimer;

    protected static void init() {

        if (GDstatic.enable && !GDstatic.testAds) {
            String url = GDstatic.GAME_API_URL + '/' + GDstatic.gameId;
            GDHttpRequest.sendHttpRequest(GDlogger.mContext, url, Request.Method.GET, null, new GDHttpCallback() {
                @Override
                public void onSuccess(JSONObject data) {

                    try {
                        boolean success = data.getBoolean("success");
                        if (success) {
                            JSONObject result = data.getJSONObject("result");
                            JSONObject game = result.getJSONObject("game");

                            GDGameData.enableAds = game.getBoolean("enableAds");
                            GDGameData.gameMd5 = game.getString("gameMd5");
                            GDGameData.preRoll = game.getBoolean("preRoll");
                            GDGameData.timeAds = game.getInt("timeAds");
                            GDGameData.title = game.getString("title");
                            GDGameData.bundleId = game.getString("androidBundleId");

                            GDutils.log(data.toString());
                            GDlogger.gDad.init(GDlogger.mContext, GDlogger.isCordovaPlugin);

                            GDlogger.gdPreloadStream.setPreloadStream(true);
                            GDlogger.gdPreloadStream.init(GDlogger.mContext, GDlogger.isCordovaPlugin);
                        } else {
                            String error = "Something went wrong fetching game data.";
                            GDutils.log(error);

                            if (GDlogger.gDad.devListener != null)
                                GDlogger.gDad.devListener.onAPINotReady(error);
                            GDstatic.enable = false;

                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                        GDutils.log("Something went wrong parsing json game data.");
                        if (GDlogger.gDad.devListener != null)
                            GDlogger.gDad.devListener.onAPINotReady("Something went wrong parsing json game data.");
                        GDstatic.enable = false;

                    }
                }

                @Override
                public void onError(VolleyError error) {
                    GDutils.log("Something went wrong fetching json game data.");
                    if (GDlogger.gDad.devListener != null)
                        GDlogger.gDad.devListener.onAPINotReady("Something went wrong fetching json game data.");
                    GDstatic.enable = false;

                }
            });
        } else if (GDstatic.testAds) {
            GDlogger.gDad.init(GDlogger.mContext, GDlogger.isCordovaPlugin);
            GDlogger.gdPreloadStream.setPreloadStream(true);
            GDlogger.gdPreloadStream.init(GDlogger.mContext, GDlogger.isCordovaPlugin);
        }
    }

    protected static void ShowBanner(String args) {

        Gson gson = new Gson();
        final GDshowObj gDshowObj;
        gDshowObj = gson.fromJson(args, GDshowObj.class);

        if ((GDGameData.enableAds || GDstatic.testAds) && gDshowObj._key != null && gDshowObj._key.equals("preroll") && GDlogger.gDad != null) {

            if (GDGameData.preRoll) {

                ShowPreloadedBanner(args);

            } else {
                if (GDlogger.gDad.devListener != null) {
                    GDlogger.gDad.devListener.onBannerFailed("Banner request failed: 'Preroll is disabled.'");
                }
            }
        } else { // so this is for midroll request

            if ((GDstatic.testAds || (GDGameData.enableAds && GDGameData.timeAds != 0)) && GDlogger.gDad != null) {

                if (gDshowObj.isInterstitial) {
                    if (GDstatic.reqInterstitialEnabled) {

                        ShowPreloadedBanner(args);
                        adInterstitialTimer = null;

                        if (!GDstatic.testAds) {
                            setAdTimer(true); // inter timer
                            GDstatic.reqInterstitialEnabled = false;
                        } else {
                            GDstatic.reqInterstitialEnabled = true;
                        }

                    } else {
                        GDutils.log("You can not invoke 'ShowBanner()' within " + GDGameData.timeAds + " min(s).");
                        if (GDlogger.gDad.devListener != null) {
                            GDlogger.gDad.devListener.onBannerFailed("You can not invoke 'ShowBanner()' within " + GDGameData.timeAds + " min(s).");
                        }
                    }
                } else {
                    if (GDstatic.reqBannerEnabled) {
                        GDlogger.gDad.showBanner(args);
                        adBannerTimer = null;
                        if (!GDstatic.testAds) {
                            setAdTimer(false); // banner timer
                            GDstatic.reqBannerEnabled = false;
                        }

                    } else {
                        GDutils.log("You can not invoke 'ShowBanner()' within " + GDGameData.timeAds + " min(s).");
                        if (GDlogger.gDad.devListener != null) {
                            GDlogger.gDad.devListener.onBannerFailed("You can not invoke 'ShowBanner()' within " + GDGameData.timeAds + " min(s).");
                        }
                    }
                }
            } else {

                if (GDlogger.gDad.devListener != null) {
                    GDlogger.gDad.devListener.onBannerFailed("Banner request failed: 'Midroll is disabled.'");
                }

            }

        }


    }

    protected static void ShowPreloadedBanner(String args) {
        // If preloaded ad exists, then show it.
        // Otherwise, make sure you request preload and also request new ad to show.
        if (GDlogger.gdPreloadStream.isPreloadedAdExist()){
            GDlogger.gdPreloadStream.showInterstitialAd();
        }
        else{
            if (!GDlogger.gdPreloadStream.isPreloadedAdLoading()) {
                if (GDlogger.gDad.devListener != null) {
                    GDlogger.gDad.devListener.onPreloadFailed("No ads found preloaded.");
                }
                GDlogger.gdPreloadStream.requestPreloadAd();
            } else {
                if (GDlogger.gDad.devListener != null) {
                    GDlogger.gDad.devListener.onPreloadFailed("A preload ad is currently being loaded.");
                }
            }
            GDlogger.gDad.showBanner(args);
        }
    }

    private static void setAdTimer(final boolean isInterstitial) {

        if (GDstatic.enable) {

            if (isInterstitial) {
                adInterstitialTimer = new Thread(new Runnable() {
                    public void run() {
                        while (true) {
                            try {
                                Thread.sleep(GDGameData.timeAds * 60000);
                                if (!GDutils.isApplicationBroughtToBackground()) {
                                    adTimerHandler(isInterstitial);
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
                adInterstitialTimer.start();
            } else {
                adBannerTimer = new Thread(new Runnable() {
                    public void run() {
                        while (true) {
                            try {
                                Thread.sleep(GDGameData.timeAds * 60000);
                                if (!GDutils.isApplicationBroughtToBackground()) {
                                    adTimerHandler(isInterstitial);
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
                adBannerTimer.start();
            }


        }
    }

    private static void adTimerHandler(boolean isInterstitial) {
        if (isInterstitial) {
            GDstatic.reqInterstitialEnabled = true;
        } else {
            GDstatic.reqBannerEnabled = true;
        }
    }
}
