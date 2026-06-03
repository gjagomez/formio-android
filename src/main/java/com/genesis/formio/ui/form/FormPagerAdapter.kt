package com.genesis.formio.ui.form

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class FormPagerAdapter(
    activity: FragmentActivity,
    private val pageCount: Int
) : FragmentStateAdapter(activity) {

    private val fragments = mutableMapOf<Int, Fragment>()

    override fun getItemCount() = pageCount

    override fun createFragment(position: Int): Fragment {
        val fragment = FormPageFragment.newInstance(position)
        fragments[position] = fragment
        return fragment
    }

    fun getFragment(position: Int): Fragment? = fragments[position]
}
