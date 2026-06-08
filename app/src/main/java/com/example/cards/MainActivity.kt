package com.example.cards

import com.example.cards.base.BaseActivity
import com.example.cards.databinding.ActivityMainBinding

class MainActivity : BaseActivity<ActivityMainBinding>() {
    override fun initUI() {
        mBinding.tvPlaceholder.text = "Legacy page placeholder"
    }
}
