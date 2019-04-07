[![Release](https://jitpack.io/v/kroegerama/bottomsheet-imagepicker.svg)](https://jitpack.io/#kroegerama/bottomsheet-imagepicker)
[![Build Status](https://travis-ci.org/kroegerama/bottomsheet-imagepicker.svg?branch=master)](https://travis-ci.org/kroegerama/bottomsheet-imagepicker)

# BottomSheet Image Picker for Android

A modern image picker implemented as [BottomSheet](https://developer.android.com/reference/android/support/design/widget/BottomSheetDialogFragment).

<p>
<img src="screens/single_select_1.png" alt="Single Selection Demo 1" width="275" />&emsp;
<img src="screens/single_select_2.png" alt="Single Selection Demo 2" width="275" />&emsp;
<img src="screens/multi_select_1.png" alt="Multi Selection Demo 1" width="275" />
</p>

## Features

1. select single/multiple images right in the bottom sheet
2. use camera to take a picture
3. choose image from gallery app
4. handles all permission requests

This library is based on [BSImagePicker](https://github.com/siralam/BSImagePicker).
I reimplemented everything in Kotlin and added some features. Also, I used the new androidX artifacts.

## How to Use

Minimum SDK: 17

### Add to Project

First make sure `jitpack` is included as a repository in your **project**'s build.gradle:  

```groovy
allprojects {
    repositories {
        //...
        maven { url 'https://jitpack.io' }
    }
}
```

And then add the below to your app's build.gradle:  

```groovy
    implementation 'com.kroegerama:bottomsheet-imagepicker:<version>'
```


### Step 1: Create your own FileProvider

Just follow the guide from [Official Android Document](https://developer.android.com/reference/android/support/v4/content/FileProvider#ProviderDefinition).
See the demo application [file_paths.xml](app/src/main/res/xml/file_paths.xml) and [AndroidManifest.xml](app/src/main/AndroidManifest.xml).

### Step 2: Implement the callback handler

The caller Activity or Fragment has to implement `BottomSheetImagePicker.OnImagesSelectedListener` to receive the selection callbacks. It will automatically be used by the image picker. No need to register a listener.

##### Kotlin

```kotlin
class AcMain: BaseActivity(), BottomSheetImagePicker.OnImagesSelectedListener {
    //...

    override fun onImagesSelected(uris: List<Uri>, tag: String?) {
        toast("Result from tag: $tag")

        imageContainer.removeAllViews()
        uris.forEach { uri ->
            val iv = LayoutInflater.from(this).inflate(R.layout.scrollitem_image, imageContainer, false) as ImageView
            imageContainer.addView(iv)
            Glide.with(this).load(uri).into(iv)
        }
    }
}
```

##### Java

```java
public class AcMainJava extends BaseActivity implements BottomSheetImagePicker.OnImagesSelectedListener {
    //...
    
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
```

You can set a `requestTag` in the builder. This is useful when you need to show more than one picker on the same page. You will receive this tag as `tag` parameter in the callback.

### Step 3: Create the image picker using the Builder

The setters are all **optional** and the builder will fallback to default values.

#### single select
##### Kotlin

```kotlin
    BottomSheetImagePicker.Builder(getString(R.string.file_provider))
        .cameraButton(ButtonType.Button)            //style of the camera link (Button in header, Image tile, None)
        .galleryButton(ButtonType.Button)           //style of the gallery link
        .singleSelectTitle(R.string.pick_single)    //header text
        .peekHeight(R.dimen.peekHeight)             //peek height of the bottom sheet
        .columnSize(R.dimen.columnSize)             //size of the columns (will be changed a little to fit)
        .requestTag("single")                       //tag can be used if multiple pickers are used
        .show(supportFragmentManager)
```
##### Java
```java
    new BottomSheetImagePicker.Builder(getString(R.string.file_provider))
       .cameraButton(ButtonType.Button)
       .galleryButton(ButtonType.Button)
       .singleSelectTitle(R.string.pick_single)
       .peekHeight(R.dimen.peekHeight)
       .columnSize(R.dimen.columnSize)
       .requestTag("single")
       .show(getSupportFragmentManager(), null);
```

#### multi select
##### Kotlin
```kotlin
    BottomSheetImagePicker.Builder(getString(R.string.file_provider))
        .multiSelect(3, 6)                  //user has to select 3 to 6 images
        .multiSelectTitles(
            R.plurals.pick_multi,           //"you have selected <count> images
            R.plurals.pick_multi_more,      //"You have to select <min-count> more images"
            R.string.pick_multi_limit       //"You cannot select more than <max> images"
        )
        .peekHeight(R.dimen.peekHeight)     //peek height of the bottom sheet
        .columnSize(R.dimen.columnSize)     //size of the columns (will be changed a little to fit)
        .requestTag("multi")                //tag can be used if multiple pickers are used
        .show(supportFragmentManager)
```
##### Java
```java
    new BottomSheetImagePicker.Builder(getString(R.string.file_provider))
        .multiSelect(3, 6)
        .multiSelectTitles(
                R.plurals.pick_multi,
                R.plurals.pick_multi_more,
                R.string.pick_multi_limit
        )
        .peekHeight(R.dimen.peekHeight)
        .columnSize(R.dimen.columnSize)
        .requestTag("multi")
        .show(getSupportFragmentManager(), null);
```

### The image picker works in activities and fragments
##### Kotlin
```kotlin
    //inside activity
        .show(supportFragmentManager)
    //inside fragment
        .show(childFragmentManager)
```
##### Java
```java
    //inside activity
        .show(getSupportFragmentManager(), null);
    //inside fragment
        .show(getChildFragmentManager(), null);
```
