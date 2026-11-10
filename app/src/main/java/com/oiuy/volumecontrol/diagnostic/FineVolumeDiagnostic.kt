package com.oiuy.volumecontrol.diagnostic

/**
 * Platform-free core of the Samsung fine-volume diagnostic.
 *
 * Contract:
 * - [snapshot] only reads. It never calls [FineVolumePort.stepTo].
 * - [testOneFineStepQuieter] is the only path that may call
 *   [FineVolumePort.stepTo], and it does so at most once per call, with
 *   `current - 1` where `current` is a fresh read taken inside the same call.
 * - There is no retry, no automatic restore, and no coarse fallback.
 * - Fine-volume methods are only looked up on Samsung devices: the
 *   [fineFactory] is never invoked otherwise.
 */
class FineVolumeDiagnostic(
    val device: DeviceInfo,
    private val publicPort: PublicVolumePort,
    fineFactory: () -> FineVolumePort,
) {
    private val finePort: FineVolumePort? = if (device.isSamsung) fineFactory() else null
    private var inFlight = false

    /** Result of the most recent tap. Passive refreshes never replace it. */
    var lastTest: TestOutcome? = null
        private set

    fun snapshot(): DiagnosticSnapshot {
        val publicVolume = publicPort.read()
        val availability = availability()
        val fine = if (finePort != null && availability is PortResult.Ok) finePort.readFine() else null
        val canTest = !inFlight &&
            availability is PortResult.Ok &&
            fine is PortResult.Ok && fine.value > 0 &&
            publicVolume is PortResult.Ok && !publicVolume.value.muted
        return DiagnosticSnapshot(
            device = device,
            publicVolume = publicVolume,
            fineAvailability = availability,
            fineValue = fine,
            canTest = canTest,
            lastTest = lastTest,
        )
    }

    /** Only called from an explicit button tap. */
    fun testOneFineStepQuieter(): TestOutcome {
        if (inFlight) {
            // Do not record: the in-flight call will record its own outcome.
            return TestOutcome.NotAttempted("A test is already running.", null, null)
        }
        inFlight = true
        try {
            val outcome = runOnce()
            lastTest = outcome
            return outcome
        } finally {
            inFlight = false
        }
    }

    private fun runOnce(): TestOutcome {
        val port = finePort
            ?: return TestOutcome.NotAttempted(
                "Not a Samsung device (manufacturer \"${device.manufacturer}\"). Fine-volume methods were not looked up.",
                null,
                null,
            )
        when (val availability = port.availability()) {
            is PortResult.Failed -> return TestOutcome.NotAttempted(
                "Samsung fine-volume methods are unavailable: ${availability.reason}",
                null,
                null,
            )
            is PortResult.Ok -> Unit
        }
        val publicBefore = publicPort.read()
        if (publicBefore is PortResult.Failed) {
            return TestOutcome.NotAttempted(
                "Could not read public media volume: ${publicBefore.reason}",
                null,
                publicBefore,
            )
        }
        if (publicBefore is PortResult.Ok && publicBefore.value.muted) {
            return TestOutcome.NotAttempted("Media volume is muted. No call was made.", null, publicBefore)
        }
        val fineBefore = port.readFine()
        val current = when (fineBefore) {
            is PortResult.Failed -> return TestOutcome.NotAttempted(
                "Could not read the current fine value: ${fineBefore.reason}",
                fineBefore,
                publicBefore,
            )
            is PortResult.Ok -> fineBefore.value
        }
        if (current <= 0) {
            return TestOutcome.NotAttempted(
                "Current fine value is $current. Nothing quieter to test. No call was made.",
                fineBefore,
                publicBefore,
            )
        }
        val requested = current - 1
        val call = port.stepTo(requested)
        val fineAfter = port.readFine()
        val publicAfter = publicPort.read()
        return TestOutcome.Attempted(
            fineBefore = current,
            requested = requested,
            call = call,
            fineAfter = fineAfter,
            publicBefore = publicBefore,
            publicAfter = publicAfter,
        )
    }

    private fun availability(): PortResult<Unit> = finePort?.availability()
        ?: PortResult.Failed("Not a Samsung device (manufacturer \"${device.manufacturer}\"). Fine-volume methods were not looked up.")
}

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val release: String,
    val sdkInt: Int,
    val buildDisplay: String,
) {
    val isSamsung: Boolean get() = manufacturer.trim().equals("samsung", ignoreCase = true)
}

data class PublicVolume(
    val index: Int,
    /** Null below Android 9, where getStreamMinVolume does not exist. */
    val min: Int?,
    val max: Int,
    val muted: Boolean,
    val route: String,
)

sealed interface PortResult<out T> {
    data class Ok<T>(val value: T) : PortResult<T>
    data class Failed(val reason: String) : PortResult<Nothing>
}

/** Read-only view of Android's public media volume. Has no write methods by design. */
interface PublicVolumePort {
    fun read(): PortResult<PublicVolume>
}

/** Samsung fine-volume access. Only [stepTo] writes. */
interface FineVolumePort {
    /** Whether both allowlisted methods were found. Must not call either method. */
    fun availability(): PortResult<Unit>
    fun readFine(): PortResult<Int>
    fun stepTo(index: Int): PortResult<Unit>
}

data class DiagnosticSnapshot(
    val device: DeviceInfo,
    val publicVolume: PortResult<PublicVolume>,
    val fineAvailability: PortResult<Unit>,
    /** Null when the fine getter was not called (not Samsung or unavailable). */
    val fineValue: PortResult<Int>?,
    val canTest: Boolean,
    val lastTest: TestOutcome?,
)

sealed interface TestOutcome {
    data class NotAttempted(
        val reason: String,
        val fineBefore: PortResult<Int>?,
        val publicBefore: PortResult<PublicVolume>?,
    ) : TestOutcome

    data class Attempted(
        val fineBefore: Int,
        val requested: Int,
        val call: PortResult<Unit>,
        val fineAfter: PortResult<Int>,
        val publicBefore: PortResult<PublicVolume>,
        val publicAfter: PortResult<PublicVolume>,
    ) : TestOutcome {
        /** True only when the read-back fine value equals the requested value. */
        val readBackMatches: Boolean
            get() = call is PortResult.Ok && fineAfter is PortResult.Ok && fineAfter.value == requested
    }
}
