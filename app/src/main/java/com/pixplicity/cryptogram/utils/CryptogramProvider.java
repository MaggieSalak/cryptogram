package com.pixplicity.cryptogram.utils;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.pixplicity.cryptogram.BuildConfig;
import com.pixplicity.cryptogram.models.Cryptogram;
import com.pixplicity.cryptogram.models.CryptogramProgress;
import com.pixplicity.cryptogram.views.CryptogramView;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

public class CryptogramProvider {

    private static final String TAG = CryptogramProvider.class.getSimpleName();

    private static final String ASSET_FILENAME = "cryptograms.json";

    private static CryptogramProvider sInstance;

    private int mCurrentIndex = -1;
    private Cryptogram[] mCryptograms;
    private HashMap<Integer, Integer> mCryptogramIds;
    private SparseArray<CryptogramProgress> mCryptogramProgress;

    private int mLastCryptogramId = -1;

    private final Gson mGson = new Gson();
    private final Random mRandom = new Random();
    private ArrayList<Integer> mRandomIndices;

    @NonNull
    public static CryptogramProvider getInstance(Context context) {
        if (sInstance == null) {
            try {
                sInstance = new CryptogramProvider(context);
            } catch (IOException e) {
                Log.e(TAG, "could not read cryptogram file", e);
                Toast.makeText(context, "Could not find any cryptograms", Toast.LENGTH_LONG).show();
            }
        }
        return sInstance;
    }

    private CryptogramProvider(@Nullable Context context) throws IOException {
        if (context != null) {
            InputStream is = context.getAssets().open(ASSET_FILENAME);
            readStream(is);
        } else {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream("assets/" + ASSET_FILENAME);
            if (is != null) {
                readStream(is);
                is.close();
            }
        }
    }

    private void readStream(InputStream is) {
        mCryptograms = mGson.fromJson(new InputStreamReader(is), Cryptogram[].class);
        int index = 0, nextId = 0;
        mCryptogramIds = new HashMap<>();
        if (BuildConfig.DEBUG) {
            LinkedList<Cryptogram> cryptograms = new LinkedList<>();
            if (CryptogramView.ENABLE_HYPHENATION) {
                cryptograms.add(new Cryptogram.Mock(
                        "AAAAAAAA\u00ADBBB\u00ADCCCCCCC\u00ADDDDDDDDDD\u00ADEEEE\u00ADFFFFFFFFFFFFF\u00ADGGGG\u00ADHHHHHHHHHH\u00ADIIIIII.",
                        null, null));
                cryptograms.add(new Cryptogram.Mock(
                        "JJJJJJJJ KKK LLLLLLL MMMMMMMMM NNNN OOOOOOOOOOOOO PPPP\u00ADQQQQQQQQQQ\u00ADRRRRRR.",
                        null, null));
            }
            cryptograms.addAll(Arrays.asList(mCryptograms));
            mCryptograms = cryptograms.toArray(new Cryptogram[cryptograms.size()]);
        }
        for (Cryptogram cryptogram : mCryptograms) {
            int id = cryptogram.getId();
            if (id == 0) {
                while (mCryptogramIds.get(nextId) != null) {
                    // Locate the next vacant spot
                    nextId++;
                }
                id = nextId;
                cryptogram.setId(id);
            }
            if (id > mLastCryptogramId) {
                mLastCryptogramId = id;
            }
            mCryptogramIds.put(id, index);
            index++;
        }
    }

    public Cryptogram[] getAll() {
        return mCryptograms;
    }

    /**
     * @return last puzzle ID
     */
    public int getLastNumber() {
        return mLastCryptogramId + 1;
    }

    public int getCount() {
        return getAll().length;
    }

    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    private int getIndexFromId(int id) {
        Integer index = mCryptogramIds.get(id);
        if (index == null) {
            return -1;
        }
        return index;
    }

    private int getIdFromIndex(int index) {
        return mCryptograms[index].getId();
    }

    @Nullable
    public Cryptogram getCurrent() {
        if (mCurrentIndex < 0) {
            mCurrentIndex = getIndexFromId(PrefsUtils.getCurrentId());
        }
        if (mCurrentIndex < 0) {
            return getNext();
        }
        return get(mCurrentIndex);
    }

    public void setCurrentIndex(int index) {
        mCurrentIndex = index;
        PrefsUtils.setCurrentId(getIdFromIndex(index));
    }

    public void setCurrentId(int id) {
        mCurrentIndex = getIndexFromId(id);
        PrefsUtils.setCurrentId(id);
    }

    @Nullable
    public Cryptogram getNext() {
        int oldIndex = mCurrentIndex;
        int newIndex = -1;
        int count = getCount();
        if (count == 0) {
            return null;
        }
        if (PrefsUtils.getRandomize()) {
            if (mRandomIndices == null) {
                mRandomIndices = new ArrayList<>();
                for (int i = 0; i < getAll().length; i++) {
                    mRandomIndices.add(i);
                }
                Collections.shuffle(mRandomIndices, mRandom);
            }
            boolean chooseNext = false;
            Iterator<Integer> iter = mRandomIndices.iterator();
            while (iter.hasNext()) {
                Integer index = iter.next();
                Cryptogram cryptogram = get(index);
                if (cryptogram == null || cryptogram.isCompleted()) {
                    // No good; eliminate this candidate and find the next
                    iter.remove();
                    cryptogram = null;
                }
                if (oldIndex == index) {
                    chooseNext = true;
                } else if (chooseNext && cryptogram != null) {
                    newIndex = index;
                    break;
                }
            }
            if (newIndex < 0 && !mRandomIndices.isEmpty()) {
                newIndex = mRandomIndices.get(0);
            }
        } else if (mCurrentIndex + 1 < getCount()) {
            newIndex = mCurrentIndex + 1;
        }
        if (newIndex > -1) {
            setCurrentIndex(newIndex);
        }
        return get(mCurrentIndex);
    }

    @Nullable
    public Cryptogram get(int index) {
        if (index < 0 || index >= mCryptograms.length) {
            return null;
        }
        return mCryptograms[index];
    }

    @Nullable
    public Cryptogram getByNumber(int number) {
        for (Cryptogram cryptogram : mCryptograms) {
            if (cryptogram.getNumber() == number) {
                return cryptogram;
            }
        }
        return null;
    }

    public long getTotalScore() {
        long score = 0;
        for (Cryptogram cryptogram : mCryptograms) {
            if (!cryptogram.isCompleted()) {
                continue;
            }
            CryptogramProgress progress = cryptogram.getProgress();
            if (!progress.hasScore(cryptogram)) {
                continue;
            }
            score += Math.round(100f * progress.getScore(cryptogram));
        }
        return score;
    }

    @NonNull
    public SparseArray<CryptogramProgress> getProgress() {
        if (mCryptogramProgress == null) {
            int failures = 0;
            mCryptogramProgress = new SparseArray<>();
            Set<String> progressStrSet = PrefsUtils.getProgress();
            if (progressStrSet != null) {
                for (String progressStr : progressStrSet) {
                    try {
                        CryptogramProgress progress = mGson.fromJson(progressStr, CryptogramProgress.class);
                        mCryptogramProgress.put(progress.getId(), progress);
                    } catch (JsonSyntaxException e) {
                        Crashlytics.setString("progressStr", progressStr);
                        Crashlytics.logException(new RuntimeException("Failed reading progress string", e));
                        progressStrSet.remove(progressStr);
                        failures++;
                    }
                }
            }
            if (failures > 0) {
                // Remove any corrupted data
                PrefsUtils.setProgress(progressStrSet);
            }
        }
        return mCryptogramProgress;
    }

    public void setProgress(CryptogramProgress progress) {
        SparseArray<CryptogramProgress> progressList = getProgress();
        progressList.put(progress.getId(), progress);

        // Now store everything
        Set<String> progressStrSet = new LinkedHashSet<>();
        for (int i = 0; i < progressList.size(); i++) {
            progressStrSet.add(mGson.toJson(progressList.valueAt(i)));
        }
        PrefsUtils.setProgress(progressStrSet);
    }

}
