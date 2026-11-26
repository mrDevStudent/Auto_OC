package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MeetAutoJoinAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastJoinClickTimeMs: Long = 0L
    private var lastRetrySignalTimeMs: Long = 0L
    private var lastMediaToggleTimeMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AutoJoinCoordinator.isSessionActive()) return
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty() && pkg !in SUPPORTED_PACKAGES) return
        inspectActiveWindow()
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    fun requestImmediateScan() {
        mainHandler.post {
            if (AutoJoinCoordinator.isSessionActive()) {
                inspectActiveWindow()
            }
        }
    }

    private fun inspectActiveWindow() {
        val root = rootInActiveWindow ?: return
        val session = AutoJoinCoordinator.session.value
        if (!session.phase.isActive()) return

        val allNodes = ArrayList<AccessibilityNodeInfo>(64)
        collectNodes(root, allNodes)

        val lowerLabels = allNodes.mapNotNull { node ->
            val combined = buildString {
                node.text?.let { append(it).append(' ') }
                node.contentDescription?.let { append(it) }
            }.trim().lowercase()
            combined.ifEmpty { null }
        }

        // 1. Check if already admitted and connected into the call
        if (isConnectedScreen(lowerLabels)) {
            AutoJoinCoordinator.onConnected(applicationContext)
            return
        }

        // 2. Check if waiting in the Google Meet lobby for host admission
        if (isWaitingRoomScreen(lowerLabels)) {
            AutoJoinCoordinator.onWaitingForAdmission(applicationContext)
            return
        }

        val now = System.currentTimeMillis()

        // 3. Check if admission timed out or failed -> tap Try again / Rejoin or trigger retry
        if (isErrorOrTimeoutScreen(lowerLabels)) {
            val retryButton = findClickableNodeByKeywords(allNodes, RETRY_BUTTON_KEYWORDS)
            if (retryButton != null && now - lastJoinClickTimeMs > 2_000L) {
                lastJoinClickTimeMs = now
                if (retryButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    AutoJoinCoordinator.onRetryRequested(
                        applicationContext,
                        "Tapped Try again / Rejoin after timeout"
                    )
                    return
                }
            }
            if (now - lastRetrySignalTimeMs > 4_000L) {
                lastRetrySignalTimeMs = now
                AutoJoinCoordinator.onRetryRequested(
                    applicationContext,
                    "No host response yet — retrying meeting link"
                )
            }
            return
        }

        // 4. Handle account chooser if Google Meet asks which Google account to use
        if (lowerLabels.any { it.contains("choose an account") || it.contains("use meet with") }) {
            val continueBtn = findClickableNodeByKeywords(allNodes, listOf("continue"))
            if (continueBtn != null && now - lastJoinClickTimeMs > 2_000L) {
                lastJoinClickTimeMs = now
                continueBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }

        // 5. Handle pre-join microphone / camera toggles before tapping Join
        if (session.autoMuteMic && !AutoJoinCoordinator.micHandledForCurrentAttempt) {
            val micOffNode = findClickableNodeByKeywords(allNodes, MIC_TURN_OFF_KEYWORDS)
            if (micOffNode != null && now - lastMediaToggleTimeMs > 500L) {
                lastMediaToggleTimeMs = now
                AutoJoinCoordinator.micHandledForCurrentAttempt = true
                micOffNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }

        if (session.autoTurnOffCamera && !AutoJoinCoordinator.camHandledForCurrentAttempt) {
            val camOffNode = findClickableNodeByKeywords(allNodes, CAM_TURN_OFF_KEYWORDS)
            if (camOffNode != null && now - lastMediaToggleTimeMs > 500L) {
                lastMediaToggleTimeMs = now
                AutoJoinCoordinator.camHandledForCurrentAttempt = true
                camOffNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }

        // 6. Tap primary Join / Ask to join button
        val joinMatch = findJoinButton(allNodes)
        if (joinMatch != null && now - lastJoinClickTimeMs > 2_000L) {
            val (clickableNode, label) = joinMatch
            lastJoinClickTimeMs = now
            if (clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                AutoJoinCoordinator.onJoinButtonTapped(applicationContext, label)
            }
        }
    }

    private fun collectNodes(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        out.add(node)
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            collectNodes(child, out)
        }
    }

    private fun isConnectedScreen(labels: List<String>): Boolean {
        val hasPreJoinOrWaiting = labels.any { label ->
            label == "join" ||
                label.contains("ask to join") ||
                label.contains("asking to join") ||
                label.contains("join now") ||
                label.contains("someone in the call will let you in") ||
                label.contains("ready to join")
        }
        if (hasPreJoinOrWaiting) return false

        return labels.any { label ->
            label.contains("leave call") ||
                label.contains("end call") ||
                label.contains("raise hand") ||
                label.contains("turn on captions") ||
                label.contains("turn off captions") ||
                label.contains("in-call messages") ||
                label.contains("send a reaction")
        }
    }

    private fun isWaitingRoomScreen(labels: List<String>): Boolean {
        return labels.any { label ->
            label.contains("asking to join") ||
                label.contains("someone in the call will let you in") ||
                label.contains("you'll join the call when") ||
                label.contains("waiting for someone to let you in") ||
                label.contains("waiting for the host")
        }
    }

    private fun isErrorOrTimeoutScreen(labels: List<String>): Boolean {
        return labels.any { label ->
            label.contains("no one responded") ||
                label.contains("couldn't join") ||
                label.contains("you can't join") ||
                label.contains("return to home screen") ||
                label.contains("something went wrong") ||
                label.contains("meeting has ended") ||
                label.contains("you left the meeting")
        }
    }

    private fun findJoinButton(nodes: List<AccessibilityNodeInfo>): Pair<AccessibilityNodeInfo, String>? {
        for (keyword in JOIN_BUTTON_KEYWORDS) {
            for (node in nodes) {
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val candidate = if (text.isNotEmpty()) text else desc
                if (candidate.isEmpty()) continue
                val lower = candidate.lowercase()

                val matches = if (keyword == "join") {
                    lower == "join" || lower == "join meeting"
                } else {
                    lower.contains(keyword)
                }

                if (matches && !lower.contains("asking to join") && !lower.contains("ready to join")) {
                    val clickable = findClickableAncestor(node)
                    if (clickable != null && clickable.isEnabled) {
                        return clickable to candidate
                    }
                }
            }
        }
        return null
    }

    private fun findClickableNodeByKeywords(
        nodes: List<AccessibilityNodeInfo>,
        keywords: List<String>
    ): AccessibilityNodeInfo? {
        for (node in nodes) {
            val combined = buildString {
                node.text?.let { append(it).append(' ') }
                node.contentDescription?.let { append(it) }
            }.trim().lowercase()
            if (combined.isEmpty()) continue
            if (keywords.any { combined.contains(it) }) {
                val clickable = findClickableAncestor(node)
                if (clickable != null && clickable.isEnabled) {
                    return clickable
                }
            }
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    companion object {
        @Volatile
        var activeInstance: MeetAutoJoinAccessibilityService? = null
            private set

        private val SUPPORTED_PACKAGES = setOf(
            "com.google.android.apps.meetings",
            "com.google.android.gm",
            "com.android.chrome"
        )

        private val JOIN_BUTTON_KEYWORDS = listOf(
            "ask to join",
            "join now",
            "switch here",
            "rejoin",
            "join"
        )

        private val RETRY_BUTTON_KEYWORDS = listOf(
            "try again",
            "rejoin",
            "ask to join"
        )

        private val MIC_TURN_OFF_KEYWORDS = listOf(
            "turn off microphone",
            "mute microphone"
        )

        private val CAM_TURN_OFF_KEYWORDS = listOf(
            "turn off camera",
            "turn off video"
        )
    }
}
