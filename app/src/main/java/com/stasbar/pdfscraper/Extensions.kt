package com.stasbar.pdfscraper

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import com.karumi.dexter.DexterBuilder
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener

fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.GONE
}

fun View.setVisibility(predicate: Boolean) {
    visibility = if (predicate) View.VISIBLE else View.GONE
}

fun EditText.addOnTextChangeListener(onTextChange: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onTextChange(s.toString())
        }

    })
}

fun DexterBuilder.SinglePermissionListener.withListener(
    permissionGranted: (PermissionGrantedResponse?) -> Unit,
    permissionDenied: (PermissionDeniedResponse?) -> Unit
): DexterBuilder {
    return withListener(object : PermissionListener {
        override fun onPermissionGranted(response: PermissionGrantedResponse?) {
            permissionGranted(response)
        }

        override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest?, token: PermissionToken?) {
        }

        override fun onPermissionDenied(response: PermissionDeniedResponse?) {
            permissionDenied(response)
        }

    })
}
