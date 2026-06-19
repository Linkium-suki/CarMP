package com.codewhale.musicplayer;

import android.app.Application;

/**
 * Application class — lightweight, just holds a reference for quick access
 * to the app context from anywhere.
 */
public class App extends Application {

    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static App getInstance() {
        return instance;
    }
}
