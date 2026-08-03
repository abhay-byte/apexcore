package com.ivarna.apexcore.fps.source

import com.ivarna.apexcore.fps.model.FpsSnapshot

interface FpsDataSource {
    suspend fun readFps(): FpsSnapshot?
    val priority: Int
}
