package com.example.rgbmems_smartphoneapp;

import android.widget.SeekBar;

public interface ISeekBarListener extends SeekBar.OnSeekBarChangeListener {
    @Override
    default void onProgressChanged(SeekBar seekBar, int i, boolean b) {

    }

    @Override
    default void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    default void onStopTrackingTouch(SeekBar seekBar) {

    }
}
