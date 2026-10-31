package org.almoha.cockpit.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import org.almoha.cockpit.data.KnownApps
import org.almoha.cockpit.media.CarNotificationListener

object Launcher {

    fun openApp(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun navigate(context: Context, packageName: String, query: String?): Boolean {
        if (query.isNullOrBlank()) return openApp(context, packageName)
        val uri = when (packageName) {
            KnownApps.WAZE -> "waze://?q=${Uri.encode(query)}&navigate=yes"
            KnownApps.MAPS -> "google.navigation:q=${Uri.encode(query)}&mode=d"
            else -> "geo:0,0?q=${Uri.encode(query)}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageName != KnownApps.MAPS) setPackage(packageName)
        }
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
        return openApp(context, packageName) || openApp(context, KnownApps.MAPS)
    }

    fun assistant(context: Context): Boolean {
        val assistantSetting = runCatching {
            Settings.Secure.getString(context.contentResolver, "assistant")
        }.getOrNull()
        val assistantPackage = assistantSetting
            ?.let { ComponentName.unflattenFromString(it)?.packageName }

        val candidates = buildList {
            if (assistantPackage == "com.google.android.googlequicksearchbox") {
                add(
                    Intent("com.google.android.googlequicksearchbox.action.CLASSIC_GSA_VOICE_SEARCH")
                        .setPackage(assistantPackage),
                )
            }
            if (!assistantPackage.isNullOrBlank()) {
                add(Intent(Intent.ACTION_ASSIST).setPackage(assistantPackage))
            }
            add(Intent(Intent.ACTION_ASSIST).addCategory(Intent.CATEGORY_DEFAULT))
        }
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                if (runCatching { context.startActivity(intent) }.isSuccess) return true
            }
        }
        return openApp(context, "com.google.android.googlequicksearchbox")
    }

    fun call(context: Context, number: String) {
        val uri = Uri.parse("tel:${Uri.encode(number)}")
        val intent = Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openDialer(context: Context) {
        val intent = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
        openApp(context, KnownApps.DIALER)
    }

    fun openContacts(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
        openDialer(context)
    }

    fun openCharging(context: Context, installed: Set<String>): Boolean {
        val pkg = listOf(
            KnownApps.ELECTROVERSE,
            KnownApps.CHARGE_MAP,
            KnownApps.ELECTROMAPS,
            KnownApps.FRESMILE,
        ).firstOrNull { it in installed }
        if (pkg != null) return openApp(context, pkg)
        if (openApp(context, KnownApps.ELECTROVERSE)) return true
        val uri = Uri.parse("geo:0,0?q=borne+de+recharge")
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    fun openNotificationAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val component = CarNotificationListener.component
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    component.flattenToString(),
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(detail) }.isSuccess) return
        }
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openOverlaySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openNotificationListenerComponent(context: Context): ComponentName =
        CarNotificationListener.component
}
