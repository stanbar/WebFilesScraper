package com.stasbar.pdfscraper

import android.view.View

fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.GONE
}

fun View.setVisibility(predicate: Boolean) {
    visibility = if (predicate) View.VISIBLE else View.GONE
}


