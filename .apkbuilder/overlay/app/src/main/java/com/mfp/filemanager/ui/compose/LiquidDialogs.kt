package com.mfp.filemanager.ui.compose

import androidx.compose.ui.graphics.Color

/**
 * One-line helpers, usable from any Fragment / Activity:
 *
 *   LiquidDialogs.confirm("Delete File?", "Move 'a.txt' to Bin?", "Delete") { vm.delete() }
 */
object LiquidDialogs {
    val Red = Color(0xFFFF3B30)
    val Blue = Color(0xFF0088FF)
    val Green = Color(0xFF34C759)

    fun confirm(
        title: String,
        message: String,
        confirmText: String,
        dismissText: String = "Cancel",
        destructive: Boolean = false,
        onCancel: () -> Unit = {},
        onConfirm: () -> Unit
    ) = LiquidDialogState.show(
        LiquidDialogSpec(
            title = title,
            message = message,
            confirmText = confirmText,
            dismissText = dismissText,
            confirmTint = if (destructive) Red else Blue,
            onConfirm = { onConfirm() },
            onDismiss = onCancel
        )
    )

    fun info(title: String, message: String? = null, rows: List<Pair<String, String>>? = null, okText: String = "OK") =
        LiquidDialogState.show(
            LiquidDialogSpec(
                title = title,
                message = message,
                rows = rows,
                confirmText = okText,
                dismissText = null
            )
        )

    fun input(
        title: String,
        initial: String,
        confirmText: String = "Rename",
        dismissText: String = "Cancel",
        onConfirm: (String) -> Unit
    ) = LiquidDialogState.show(
        LiquidDialogSpec(
            title = title,
            inputInitial = initial,
            confirmText = confirmText,
            dismissText = dismissText,
            onConfirm = onConfirm
        )
    )

    fun choices(
        title: String,
        items: List<String>,
        dismissText: String? = "Cancel",
        onCancel: () -> Unit = {},
        onChoice: (Int) -> Unit
    ) = LiquidDialogState.show(
        LiquidDialogSpec(
            title = title,
            choices = items,
            confirmText = null,
            dismissText = dismissText,
            onChoice = onChoice,
            onDismiss = onCancel
        )
    )
}
