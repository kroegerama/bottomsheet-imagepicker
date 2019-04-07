package com.kroegerama.imgpicker.demo;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.kroegerama.imgpicker.BottomSheetImagePicker;
import com.kroegerama.imgpicker.ButtonType;
import com.kroegerama.kaiteki.baseui.BaseActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AcMainJava extends BaseActivity implements BottomSheetImagePicker.OnImagesSelectedListener {

    private ViewGroup imageContainer;

    @Override
    protected int getLayoutResource() {
        return R.layout.ac_main;
    }

    @Override
    protected void setupGUI() {
        Button btnSingle = findViewById(R.id.btnPickSingle);
        Button btnMulti = findViewById(R.id.btnPickMulti);
        Button btnClear = findViewById(R.id.btnClear);

        imageContainer = findViewById(R.id.imageContainer);

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageContainer.removeAllViews();
            }
        });
        btnSingle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new BottomSheetImagePicker.Builder(getString(R.string.file_provider))
                        .cameraButton(ButtonType.Button)
                        .galleryButton(ButtonType.Button)
                        .singleSelectTitle(R.string.pick_single)
                        .peekHeight(R.dimen.peekHeight)
                        .requestTag("single")
                        .show(getSupportFragmentManager(), null);
            }
        });
        btnMulti.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new BottomSheetImagePicker.Builder(getString(R.string.file_provider))
                        .columnSize(R.dimen.columnSize)
                        .multiSelect(3, 6)
                        .multiSelectTitles(
                                R.plurals.pick_multi,
                                R.plurals.pick_multi_more,
                                R.string.pick_multi_limit
                        )
                        .requestTag("multi")
                        .show(getSupportFragmentManager(), null);
            }
        });
    }

    @Override
    public void onImagesSelected(@NotNull List<? extends Uri> uris, @Nullable String tag) {
        imageContainer.removeAllViews();
        for (Uri uri : uris) {
            ImageView iv = (ImageView) LayoutInflater.from(this).inflate(R.layout.scrollitem_image, imageContainer, false);
            imageContainer.addView(iv);
            Glide.with(this).load(uri).into(iv);
        }
    }
}
