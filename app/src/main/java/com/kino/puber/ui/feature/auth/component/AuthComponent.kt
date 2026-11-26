package com.kino.puber.ui.feature.auth.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.ApiDomainDialog
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.ScaffoldMessage
import com.kino.puber.ui.feature.auth.model.AuthAction
import com.kino.puber.ui.feature.auth.model.AuthViewState
import com.kino.puber.ui.feature.auth.vm.AuthVM
import com.kino.puber.core.di.puberViewModel
import kotlinx.coroutines.delay

@Composable
internal fun AuthScreenComponent() {
    val vm = puberViewModel<AuthVM>()
    val viewState by vm.collectViewState()
    val message by vm.collectMessage()
    val focusRequester = remember { FocusRequester() }
    val dialogState = viewState.apiDomainDialog()

    LaunchedEffect(dialogState) {
        if (dialogState != null) return@LaunchedEffect
        delay(REQUEST_FOCUS_DELAY_MS)
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (dialogState != null) return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.isMirrorShortcut()) {
                    vm.onAction(AuthAction.OpenApiDomainDialog)
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (val state = viewState) {
            is AuthViewState.Content -> CodeInfo(
                code = state.code,
                url = state.url,
                timeLeft = state.timeLeft,
                accountBlocked = state.accountBlocked,
            )
            is AuthViewState.Loading -> LoadingInfo(
                showMirrorHint = state.showMirrorHint,
                statusMessage = state.statusMessage,
                accountBlocked = state.accountBlocked,
            )
        }
        ApiDomainDialog(
            state = dialogState,
            onSave = { vm.onAction(AuthAction.SaveApiDomain(it)) },
            onReset = { vm.onAction(AuthAction.ResetApiDomain) },
            onDetect = { vm.onAction(AuthAction.DetectApiDomain) },
            onDismiss = { vm.onAction(AuthAction.CloseApiDomainDialog) },
        )
        ScaffoldMessage(
            message = message,
            onAction = vm::onAction,
        )
    }
}

@Composable
private fun LoadingInfo(
    showMirrorHint: Boolean,
    statusMessage: String?,
    accountBlocked: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        FullScreenProgressIndicator()
        if (accountBlocked) {
            AccountBlockedLine(
                Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 160.dp),
            )
        }
        if (statusMessage != null) {
            Text(
                text = statusMessage,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 160.dp),
            )
        }
        if (showMirrorHint) {
            Text(
                text = stringResource(R.string.auth_mirror_loading_hint),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 64.dp, vertical = 48.dp),
            )
        }
    }
}

@Composable
internal fun CodeInfo(code: String, url: String, timeLeft: String, accountBlocked: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (accountBlocked) {
            AccountBlockedLine()
            Spacer(modifier = Modifier.height(20.dp))
        }
        Text(code, fontSize = 32.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Image(
            painter = painterResource(R.drawable.qr_device),
            contentDescription = null,
            modifier = Modifier.size(200.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(url)
        Spacer(modifier = Modifier.height(16.dp))
        Text(timeLeft)
        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.auth_mirror_content_hint))
    }
}

@Composable
private fun AccountBlockedLine(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.auth_account_blocked),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 560.dp).then(modifier),
    )
}

private fun AuthViewState.apiDomainDialog() = when (this) {
    is AuthViewState.Content -> apiDomainDialog
    is AuthViewState.Loading -> apiDomainDialog
}

private fun android.view.KeyEvent.isMirrorShortcut(): Boolean {
    if (action != android.view.KeyEvent.ACTION_DOWN) return false
    val isSelectKey = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == android.view.KeyEvent.KEYCODE_ENTER
    return isSelectKey &&
        (isLongPress || repeatCount >= LONG_PRESS_REPEAT_COUNT)
}

private const val LONG_PRESS_REPEAT_COUNT = 1
private const val REQUEST_FOCUS_DELAY_MS = 100L

@Preview
@Composable
internal fun CodeInfoPreview() {
    CodeInfo(code = "123456", url = "https://www.example.com", timeLeft = "02:00")
}

@Preview(device = "spec:width=960dp,height=540dp,dpi=320")
@Composable
internal fun CodeInfoBlockedPreview() {
    PuberTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CodeInfo(code = "123456", url = "https://www.example.com", timeLeft = "02:00", accountBlocked = true)
        }
    }
}
