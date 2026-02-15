package com.saulhdev.feeder.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class Utilities {
    fun restartApp(context: Context) {
        val pm = context.packageManager

        var intent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        val componentName = intent.resolveActivity(pm)
        if (componentName != null && context.packageName != componentName.packageName) {
            intent = pm
                .getLaunchIntentForPackage(context.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
                ?: intent
        }

        restartApp(context, intent)
    }

    fun restartApp(
        context: Context,
        intent: Intent?,
    ) {
        context.startActivity(intent)

        // Create a pending intent so the application is restarted after System.exit(0) was called.
        // We use an AlarmManager to call this intent in 100ms
        val mPendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent)

        exitProcess(0)
    }

    companion object {
        @JvmStatic
        fun isRtl(res: Resources): Boolean = res.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

        @JvmStatic
        fun boundToRange(
            value: Float,
            lowerBound: Float,
            upperBound: Float,
        ): Float = max(lowerBound, min(value, upperBound))

        @JvmStatic
        fun getDescendantCoordRelativeToAncestor(
            descendant: View,
            ancestor: View,
            coord: FloatArray,
            includeRootScroll: Boolean,
        ): Float =
            getDescendantCoordRelativeToAncestor(
                descendant,
                ancestor,
                coord,
                includeRootScroll,
                false,
            )

        fun getDescendantCoordRelativeToAncestor(
            descendant: View,
            ancestor: View,
            coord: FloatArray,
            includeRootScroll: Boolean,
            ignoreTransform: Boolean,
        ): Float {
            var scale = 1.0f
            var v: View? = descendant
            while (v != ancestor && v != null) {
                // For TextViews, scroll has a meaning which relates to the text position
                // which is very strange... ignore the scroll.
                if (v != descendant || includeRootScroll) {
                    offsetPoints(coord, -v.scrollX.toFloat(), -v.scrollY.toFloat())
                }

                if (!ignoreTransform) {
                    v.matrix.mapPoints(coord)
                }

                offsetPoints(coord, v.left.toFloat(), v.top.toFloat())
                scale *= v.scaleX

                v = v.parent as? View
            }
            return scale
        }

        fun offsetPoints(
            points: FloatArray,
            offsetX: Float,
            offsetY: Float,
        ) {
            var i = 0
            while (i + 1 < points.size) {
                points[i] += offsetX
                points[i + 1] += offsetY
                i += 2
            }
        }
    }
}
