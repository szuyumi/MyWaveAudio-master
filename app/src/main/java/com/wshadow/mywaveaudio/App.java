package com.wshadow.mywaveaudio;

import android.app.Application;
import android.content.Context;

/**
 * Created by WelkinShadow on 2018/2/9.
 */

public class App extends Application{
    private static Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
    }

    public static Context getInstance() {
        return mContext;
    }
}
