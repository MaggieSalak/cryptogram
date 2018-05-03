package com.pixplicity.cryptogram;

import android.app.Application;
import android.content.ContextWrapper;

import com.crashlytics.android.Crashlytics;
import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
import com.pixplicity.cryptogram.api.ApiService;
import com.pixplicity.cryptogram.services.CryptogramJobService;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.pixplicity.cryptogram.utils.UpdateManager;
import com.pixplicity.easyprefs.library.Prefs;

import java.util.concurrent.TimeUnit;

import io.fabric.sdk.android.Fabric;
import io.realm.Realm;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class CryptogramApp extends Application {

    public static final String CONTENT_LANDING = "landing";
    public static final String CONTENT_ACHIEVEMENTS = "achievements";
    public static final String CONTENT_LEADERBOARDS = "leaderboards";
    public static final String CONTENT_STATISTICS = "statistics";
    public static final String CONTENT_SETTINGS = "settings";
    public static final String CONTENT_CONTRIBUTE = "contribute";
    public static final String CONTENT_HOW_TO_PLAY = "how-to-play";
    public static final String CONTENT_ABOUT = "about";
    public static final String EVENT_LEVEL_START = "level_start";
    public static final String EVENT_LEVEL_END = "level_end";

    private static CryptogramApp sInstance;

    private ApiService mApiService;

    private FirebaseAnalytics mFirebaseAnalytics;

    public CryptogramApp() {
        super();
        sInstance = this;
    }

    public static CryptogramApp getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // Initialize Crashlytics
        Fabric.with(this, new Crashlytics());

        // Initialize the Prefs class
        new Prefs.Builder()
                .setContext(this)
                .setMode(ContextWrapper.MODE_PRIVATE)
                .setPrefsName(getPackageName())
                .setUseDefaultSharedPreference(true)
                .build();

        // Prepare Retrofit
        {
            int cacheSize = 10 * 1024 * 1024; // 10 MB
            Cache cache = new Cache(getCacheDir(), cacheSize);

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .cache(cache)
                    .addInterceptor(logging)
                    .build();
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://cryptogram-183212.appspot.com/")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            mApiService = retrofit.create(ApiService.class);
        }

        // Prepare Realm
        Realm.init(this);

        // Handle any app updates
        UpdateManager.init(this);

        // Schedule jobs
        {
            // Create a new dispatcher using the Google Play driver
            FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(this));

            final int periodicity = (int) TimeUnit.HOURS.toSeconds(12);
            final int toleranceInterval = (int) TimeUnit.HOURS.toSeconds(6);

            Job periodicDownloadJob = dispatcher.newJobBuilder()
                                                .setService(CryptogramJobService.class)
                                                .setTag(CryptogramJobService.TAG_PERIODIC_DOWNLOAD)
                                                .setConstraints(
                                                        // only run on an unmetered network
                                                        Constraint.ON_UNMETERED_NETWORK
                                                )
                                                .setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL)
                                                .setTrigger(Trigger.executionWindow(periodicity, periodicity + toleranceInterval))
                                                .setLifetime(Lifetime.FOREVER)
                                                .setRecurring(true)
                                                .setReplaceCurrent(true)
                                                .build();
            int result = dispatcher.schedule(periodicDownloadJob);
            if (result != FirebaseJobDispatcher.SCHEDULE_RESULT_SUCCESS) {
                Crashlytics.logException(new IllegalStateException("FirebaseJobDispatcher couldn't schedule job; result=" + result));
            }
        }
    }

    public ApiService getApiService() {
        return mApiService;
    }

    public FirebaseAnalytics getFirebaseAnalytics() {
        return mFirebaseAnalytics;
    }

}
