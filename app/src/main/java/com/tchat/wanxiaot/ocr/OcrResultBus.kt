package com.tchat.wanxiaot.ocr

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object OcrResultBus {
    private val _results = MutableSharedFlow<OcrExtractedCredentials>(extraBufferCapacity = 1)
    val results: SharedFlow<OcrExtractedCredentials> = _results.asSharedFlow()

    fun tryEmit(result: OcrExtractedCredentials) {
        _results.tryEmit(result)
    }
}

