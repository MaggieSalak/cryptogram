package com.pixplicity.cryptogram.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.AppCompatButton;
import android.util.AttributeSet;

import com.pixplicity.cryptogram.R;
import com.pixplicity.cryptogram.utils.KeyboardUtils;


public class KeyboardButton extends AppCompatButton implements KeyboardUtils.Contract {

    private int mKeyValue;

    public KeyboardButton(Context context) {
        super(context);
        init(context, null, 0);
    }

    public KeyboardButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public KeyboardButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(final Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.KeyboardButton,
                defStyleAttr, 0);

        try {
            mKeyValue = a.getInteger(R.styleable.KeyboardButton_key, 0);
        } finally {
            a.recycle();
        }

        setOnClickListener(view -> KeyboardUtils.dispatch(KeyboardButton.this));
        setText(KeyboardUtils.getKeyText(this));
    }

    @Override
    public int getKeyIndex() {
        return mKeyValue;
    }

}
