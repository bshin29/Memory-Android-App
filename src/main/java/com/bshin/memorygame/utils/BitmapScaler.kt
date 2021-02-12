package com.bshin.memorygame.utils

import android.graphics.Bitmap

//used to scale down images before uploading to firebase DB
object BitmapScaler {

    //scale and maintain aspect ratio given a desired width
    fun scaleToFitWitdh(b: Bitmap, width:Int):Bitmap {
        val factor = width/b.width.toFloat()
        return Bitmap.createScaledBitmap(b,width, (b.height*factor).toInt(),true)
    }

    //scale and maintain aspect ratio given a desired height
    fun scaleToFitHeight(b:Bitmap, height:Int):Bitmap{
        val factor = height/b.height.toFloat()
        return Bitmap.createScaledBitmap(b,(b.width*factor).toInt(),height,true)
    }
}
