package com.goodwy.ftpmanager.extensions

import android.view.View

fun View.setWidth(size: Int) {
    val lp = layoutParams
    lp.width = size
    layoutParams = lp
}
