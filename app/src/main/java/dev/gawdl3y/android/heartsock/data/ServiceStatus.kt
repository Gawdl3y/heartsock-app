package dev.gawdl3y.android.heartsock.data

import androidx.annotation.StringRes
import dev.gawdl3y.android.heartsock.R

enum class ServiceStatus(@StringRes val stringResource: Int) {
	DISCONNECTED(R.string.status_disconnected),
	AWAITING_WIFI(R.string.status_awaiting_wifi),
	SCANNING(R.string.status_scanning),
	CONNECTING(R.string.status_connecting),
	CONNECTED(R.string.status_connected),
	RECONNECTING(R.string.status_reconnecting)
}