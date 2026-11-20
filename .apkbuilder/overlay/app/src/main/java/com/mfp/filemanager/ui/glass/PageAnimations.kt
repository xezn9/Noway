package com.mfp.filemanager.ui.glass

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import androidx.core.widget.NestedScrollView
import androidx.navigation.NavOptions
import androidx.recyclerview.widget.RecyclerView
import com.mfp.filemanager.R

/** Same enter / exit animations for every navigation in the app. */
object NavAnims {
    val options: NavOptions by lazy {
        NavOptions.Builder()
            .setEnterAnim(R.anim.pop_enter)
            .setExitAnim(R.anim.pop_exit)
            .setPopEnterAnim(R.anim.pop_pop_enter)
            .setPopExitAnim(R.anim.pop_pop_exit)
            .build()
    }
}

/** Content animations that run when a screen's view is created: lists cascade in, cards float up one by one. */
object PageAnimations {

    fun play(root: View) {
        val rv = findFirst(root) { it is RecyclerView } as? RecyclerView
        if (rv != null) {
            cascadeList(rv)
            return
        }
        val scroll = findFirst(root) { it is NestedScrollView } as? NestedScrollView ?: return
        val content = scroll.getChildAt(0) as? ViewGroup ?: return
        floatChildren(content)
    }

    private fun cascadeList(rv: RecyclerView) {
        rv.layoutAnimation = AnimationUtils.loadLayoutAnimation(rv.context, R.anim.layout_list_enter)
        val adapter = rv.adapter ?: return
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            private var done = false
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (!done && adapter.itemCount > 0) {
                    done = true
                    rv.scheduleLayoutAnimation() // first real data arrived -> cascade the rows in
                }
            }
        })
    }

    private fun floatChildren(content: ViewGroup) {
        val dist = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 28f, content.resources.displayMetrics
        )
        for (i in 0 until content.childCount) {
            val c = content.getChildAt(i)
            c.alpha = 0f
            c.translationY = dist
            c.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(90L + i * 60L)
                .setDuration(460)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()
        }
    }

    private fun findFirst(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findFirst(v.getChildAt(i), pred)?.let { return it }
        return null
    }
}
