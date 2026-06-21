package com.lm.shiguang.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.util.Log

// 独立公开的广播接收器类
class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val receivedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
        Log.d(PopupManager.TAG_PUBLIC, "收到下载广播，ID: $receivedId, 目标ID: ${PopupManager.downloadIdPublic}")

        if (receivedId == PopupManager.downloadIdPublic && receivedId != -1L && context != null) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(receivedId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val statusColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val reasonColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                if (statusColumnIndex != -1) {
                    val status = cursor.getInt(statusColumnIndex)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        Log.d(PopupManager.TAG_PUBLIC, "下载成功，开始安装")
                        PopupManager.activityRef?.let { PopupManager.installApk(it) }
                    } else {
                        val reason = if (reasonColumnIndex != -1) cursor.getInt(reasonColumnIndex) else -1
                        Log.e(PopupManager.TAG_PUBLIC, "下载失败，状态码: $status, 原因: $reason")
                        Toast.makeText(context, "APK下载失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(PopupManager.TAG_PUBLIC, "获取下载状态列索引失败")
                    Toast.makeText(context, "下载状态获取失败", Toast.LENGTH_SHORT).show()
                }
            }
            cursor.close()
        }
    }
}