package com.smrtask

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.json.JSONObject

class MainViewModel : ViewModel() {
    val responseHtml = MutableLiveData<String>()
    val lastInput = MutableLiveData<String>()

    // To store chat history as a list of JSONObjects
    val chatHistory = mutableListOf<JSONObject>()
}