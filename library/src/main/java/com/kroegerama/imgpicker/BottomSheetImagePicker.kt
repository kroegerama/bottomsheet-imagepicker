package com.kroegerama.imgpicker

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DimenRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kroegerama.kaiteki.recyclerview.layout.AutofitLayoutManager
import kotlinx.android.synthetic.main.imagepicker.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BottomSheetImagePicker internal constructor() :
    BottomSheetDialogFragment(), LoaderManager.LoaderCallbacks<Cursor> {

    private var currentPhotoUri: Uri? = null

    private var isMultiSelect = false
    private var multiSelectMin = 1
    private var multiSelectMax = Int.MAX_VALUE

    private var providerAuthority = ""
    private var requestTag = ""

    private var showCameraTile = false
    private var showCameraButton = true
    private var showGalleryTile = false
    private var showGalleryButton = true

    @StringRes
    private var resTitleSingle = R.string.imagePickerSingle
    @PluralsRes
    private var resTitleMulti = R.plurals.imagePickerMulti
    @PluralsRes
    private var resTitleMultiMore = R.plurals.imagePickerMultiMore
    @StringRes
    private var resTitleMultiLimit = R.string.imagePickerMultiLimit
    @DimenRes
    private var peekHeight = R.dimen.imagePickerPeekHeight
    @DimenRes
    private var columnSizeRes = R.dimen.imagePickerColumnSize
    @StringRes
    private var loadingRes = R.string.imagePickerLoading
    @StringRes
    private var emptyRes = R.string.imagePickerEmpty

    private var onImagesSelectedListener: OnImagesSelectedListener? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>

    private val adapter by lazy {
        ImageTileAdapter(
            isMultiSelect,
            showCameraTile,
            showGalleryTile,
            ::tileClick,
            ::selectionCountChanged
        )
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnImagesSelectedListener) {
            onImagesSelectedListener = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadArguments()
        if (requireContext().hasReadStoragePermission) {
            LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this)
        } else {
            requestReadStoragePermission(REQUEST_PERMISSION_READ_STORAGE)
        }
        if (savedInstanceState != null) {
            currentPhotoUri = savedInstanceState.getParcelable(STATE_CURRENT_URI)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        inflater.inflate(R.layout.imagepicker, container, false).also {
            (parentFragment as? OnImagesSelectedListener)?.let { onImagesSelectedListener = it }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvHeader.setOnClickListener {
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                recycler.smoothScrollToPosition(0)
            }
        }
        if (showGalleryButton) {
            btnGallery.isVisible = true
            btnGallery.setOnClickListener { launchGallery() }
        }
        if (showCameraButton) {
            btnCamera.isVisible = true
            btnCamera.setOnClickListener { launchCamera() }
        }
        tvHeader.setText(resTitleSingle)
        tvEmpty.setText(loadingRes)

        if (isMultiSelect) {
            btnCamera.isVisible = false
            btnGallery.isVisible = false
            btnDone.isVisible = true
            btnDone.setOnClickListener {
                onImagesSelectedListener?.onImagesSelected(adapter.getSelectedImages(), requestTag)
                dismissAllowingStateLoss()
            }
            btnClearSelection.isVisible = true
            btnClearSelection.setOnClickListener { adapter.clear() }
        }

        recycler.layoutManager = AutofitLayoutManager(requireContext(), columnSizeRes)
        (recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        recycler.adapter = adapter

        val oldSelection = savedInstanceState?.getIntArray(STATE_SELECTION)
        if (oldSelection != null) {
            adapter.selection = oldSelection.toHashSet()
        }
        selectionCountChanged(adapter.selection.size)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener {
                val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
                bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
                bottomSheetBehavior.peekHeight = resources.getDimensionPixelSize(peekHeight)
                bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback)
            }
        }

    private val bottomSheetCallback by lazy {
        object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                view?.alpha = if (slideOffset < 0f) 1f + slideOffset else 1f
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    private fun tileClick(tile: ClickedTile) {
        when (tile) {
            is ClickedTile.CameraTile -> {
                launchCamera()
            }
            is ClickedTile.GalleryTile -> {
                launchGallery()
            }
            is ClickedTile.ImageTile -> {
                onImagesSelectedListener?.onImagesSelected(listOf(tile.uri), requestTag)
                dismissAllowingStateLoss()
            }
        }
    }

    private fun selectionCountChanged(count: Int) {
        if (!isMultiSelect) return
        when {
            count < multiSelectMin -> {
                val delta = multiSelectMin - count
                tvHeader.text = resources.getQuantityString(resTitleMultiMore, delta, delta)
            }
            count > multiSelectMax -> tvHeader.text = getString(resTitleMultiLimit, multiSelectMax)
            else -> tvHeader.text = resources.getQuantityString(resTitleMulti, count, count)
        }
        btnDone.isEnabled = count in multiSelectMin..multiSelectMax
        btnDone.animate().alpha(if (btnDone.isEnabled) 1f else .2f)
        btnClearSelection.isEnabled = count > 0
        btnClearSelection.animate().alpha(if (btnClearSelection.isEnabled) 1f else .2f)
    }

    private fun launchCamera() {
        if (!requireContext().hasWriteStoragePermission) {
            requestWriteStoragePermission(REQUEST_PERMISSION_WRITE_STORAGE)
            return
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(requireContext().packageManager) == null) return
        val photoUri = try {
            getPhotoUri()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "could not prepare image file", e)
            return
        }
        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoUri = photoUri

        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        requireContext().packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        ).forEach { info ->
            val packageName = info.activityInfo.packageName
            requireContext().grantUriPermission(
                packageName,
                photoUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        startActivityForResult(intent, REQUEST_PHOTO)
    }

    private fun launchGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    private fun getPhotoUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = requireContext().contentResolver
            val contentVals = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, getImageFileName() + ".jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")

                //put images in DCIM folder
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/")
            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentVals)
        } else {
            val imageFileName = getImageFileName()
            val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            storageDir.mkdirs()
            val image = File.createTempFile(imageFileName + "_", ".jpg", storageDir)

            //no need to create empty file; camera app will create it on success
            val success = image.delete()
            if (!success && BuildConfig.DEBUG) {
                Log.d(TAG, "Failed to delete temp file: $image")
            }
            FileProvider.getUriForFile(requireContext(), providerAuthority, image)
        }

    @SuppressLint("SimpleDateFormat")
    private fun getImageFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().time)
        return "IMG_$timeStamp"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_PERMISSION_READ_STORAGE ->
                if (grantResults.isPermissionGranted)
                    LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this)
                else dismissAllowingStateLoss()
            REQUEST_PERMISSION_WRITE_STORAGE ->
                if (grantResults.isPermissionGranted)
                    launchCamera()
                else
                    Toast.makeText(
                        requireContext(),
                        R.string.toastImagePickerNoWritePermission,
                        Toast.LENGTH_LONG
                    ).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        when (requestCode) {
            REQUEST_PHOTO -> {
                notifyGallery()
                currentPhotoUri?.let { uri ->
                    onImagesSelectedListener?.onImagesSelected(listOf(uri), requestTag)
                }
                dismissAllowingStateLoss()
                return
            }
            REQUEST_GALLERY -> {
                data?.data?.let { uri ->
                    onImagesSelectedListener?.onImagesSelected(listOf(uri), requestTag)
                }
                dismissAllowingStateLoss()
                return
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_CURRENT_URI, currentPhotoUri)
        outState.putIntArray(STATE_SELECTION, adapter.selection.toIntArray())
    }

    private fun notifyGallery() {
        context?.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
            data = currentPhotoUri
        })
    }

    private fun loadArguments() {
        val args = arguments ?: return
        isMultiSelect = args.getBoolean(KEY_MULTI_SELECT, isMultiSelect)
        multiSelectMin = args.getInt(KEY_MULTI_SELECT_MIN, multiSelectMin)
        multiSelectMax = args.getInt(KEY_MULTI_SELECT_MAX, multiSelectMax)
        providerAuthority = args.getString(KEY_PROVIDER, this::class.java.canonicalName)
        showCameraTile = args.getBoolean(KEY_SHOW_CAMERA_TILE, showCameraTile)
        showCameraButton = args.getBoolean(KEY_SHOW_CAMERA_BTN, showCameraButton)
        showGalleryTile = args.getBoolean(KEY_SHOW_GALLERY_TILE, showGalleryTile)
        showGalleryButton = args.getBoolean(KEY_SHOW_GALLERY_BTN, showGalleryButton)
        columnSizeRes = args.getInt(KEY_COLUMN_SIZE_RES, columnSizeRes)
        requestTag = args.getString(KEY_REQUEST_TAG, requestTag)

        resTitleSingle = args.getInt(KEY_TITLE_RES_SINGLE, resTitleSingle)
        resTitleMulti = args.getInt(KEY_TITLE_RES_MULTI, resTitleMulti)
        resTitleMultiMore = args.getInt(KEY_TITLE_RES_MULTI_MORE, resTitleMultiMore)
        resTitleMultiLimit = args.getInt(KEY_TITLE_RES_MULTI_LIMIT, resTitleMultiLimit)

        peekHeight = args.getInt(KEY_PEEK_HEIGHT, peekHeight)

        emptyRes = args.getInt(KEY_TEXT_EMPTY, emptyRes)
        loadingRes = args.getInt(KEY_TEXT_LOADING, loadingRes)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        if (id != LOADER_ID) throw IllegalStateException("illegal loader id: $id")
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC"
        return CursorLoader(requireContext(), uri, projection, null, null, sortOrder)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        progress.isVisible = false
        tvEmpty.setText(emptyRes)
        data ?: return

        val columnIndex = data.getColumnIndex(MediaStore.Images.Media._ID)
        val items = ArrayList<Uri>()
        while (items.size < MAX_CURSOR_IMAGES && data.moveToNext()) {
            val id = data.getLong(columnIndex)
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id
            )
            items.add(contentUri)
        }
        data.moveToFirst()
        adapter.imageList = items
        tvEmpty.isVisible = items.size == 0
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        adapter.imageList = emptyList()
    }

    interface OnImagesSelectedListener {
        fun onImagesSelected(uris: List<Uri>, tag: String?)
    }

    companion object {
        private const val TAG = "BottomSheetImagePicker"

        private const val LOADER_ID = 0x1337

        private const val REQUEST_PERMISSION_READ_STORAGE = 0x2000
        private const val REQUEST_PERMISSION_WRITE_STORAGE = 0x2001

        private const val REQUEST_PHOTO = 0x3000
        private const val REQUEST_GALLERY = 0x3001

        private const val KEY_PROVIDER = "provider"
        private const val KEY_REQUEST_TAG = "requestTag"

        private const val KEY_MULTI_SELECT = "multiSelect"
        private const val KEY_MULTI_SELECT_MIN = "multiSelectMin"
        private const val KEY_MULTI_SELECT_MAX = "multiSelectMax"
        private const val KEY_SHOW_CAMERA_TILE = "showCameraTile"
        private const val KEY_SHOW_CAMERA_BTN = "showCameraButton"
        private const val KEY_SHOW_GALLERY_TILE = "showGalleryTile"
        private const val KEY_SHOW_GALLERY_BTN = "showGalleryButton"
        private const val KEY_COLUMN_SIZE_RES = "columnCount"

        private const val KEY_TITLE_RES_SINGLE = "titleResSingle"
        private const val KEY_TITLE_RES_MULTI = "titleResMulti"
        private const val KEY_TITLE_RES_MULTI_MORE = "titleResMultiMore"
        private const val KEY_TITLE_RES_MULTI_LIMIT = "titleResMultiLimit"

        private const val KEY_TEXT_EMPTY = "emptyText"
        private const val KEY_TEXT_LOADING = "loadingText"

        private const val KEY_PEEK_HEIGHT = "peekHeight"

        private const val STATE_CURRENT_URI = "stateUri"
        private const val STATE_SELECTION = "stateSelection"

        private const val MAX_CURSOR_IMAGES = 512
    }

    class Builder(provider: String) {

        private val args = Bundle().apply {
            putString(KEY_PROVIDER, provider)
        }

        fun requestTag(requestTag: String) = args.run {
            putString(KEY_REQUEST_TAG, requestTag)
            this@Builder
        }

        fun multiSelect(min: Int = 1, max: Int = Int.MAX_VALUE) = args.run {
            putBoolean(KEY_MULTI_SELECT, true)
            putInt(KEY_MULTI_SELECT_MIN, min)
            putInt(KEY_MULTI_SELECT_MAX, max)
            this@Builder
        }

        fun columnSize(@DimenRes columnSizeRes: Int) = args.run {
            putInt(KEY_COLUMN_SIZE_RES, columnSizeRes)
            this@Builder
        }

        fun cameraButton(type: ButtonType) = args.run {
            putBoolean(KEY_SHOW_CAMERA_BTN, type == ButtonType.Button)
            putBoolean(KEY_SHOW_CAMERA_TILE, type == ButtonType.Tile)
            this@Builder
        }

        fun galleryButton(type: ButtonType) = args.run {
            putBoolean(KEY_SHOW_GALLERY_BTN, type == ButtonType.Button)
            putBoolean(KEY_SHOW_GALLERY_TILE, type == ButtonType.Tile)
            this@Builder
        }

        fun singleSelectTitle(@StringRes titleRes: Int) = args.run {
            putInt(KEY_TITLE_RES_SINGLE, titleRes)
            this@Builder
        }

        fun peekHeight(@DimenRes peekHeightRes: Int) = args.run {
            putInt(KEY_PEEK_HEIGHT, peekHeightRes)
            this@Builder
        }

        fun emptyText(@StringRes emptyRes: Int) = args.run {
            putInt(KEY_TEXT_EMPTY, emptyRes)
            this@Builder
        }

        fun loadingText(@StringRes loadingRes: Int) = args.run {
            putInt(KEY_TEXT_LOADING, loadingRes)
            this@Builder
        }

        fun multiSelectTitles(
            @PluralsRes titleCount: Int,
            @PluralsRes titleNeedMore: Int,
            @StringRes titleLimit: Int
        ) = args.run {
            putInt(KEY_TITLE_RES_MULTI, titleCount)
            putInt(KEY_TITLE_RES_MULTI_MORE, titleNeedMore)
            putInt(KEY_TITLE_RES_MULTI_LIMIT, titleLimit)
            this@Builder
        }

        fun build() = BottomSheetImagePicker().apply { arguments = args }

        fun show(fm: FragmentManager, tag: String? = null) = build().show(fm, tag)

    }
}

enum class ButtonType {
    None, Button, Tile
}
