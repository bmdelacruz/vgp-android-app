package com.bmdelacruz.vgp

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.bmdelacruz.vgp.ControllerEvent.Companion.makeEvent
import com.bmdelacruz.vgp.databinding.ActivityFullscreenBinding
import com.google.android.material.snackbar.Snackbar
import io.grpc.Deadline
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

class ControllerActivity : AppCompatActivity() {
    private val systemUiVisibilityControlChannel = Channel<Boolean>()
    private val waitChannel = Channel<Unit>()
    private val forceFeedbackMap = mutableMapOf<Int, ForceFeedback>()

    private val vm by viewModels<VM>()

    private val vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private lateinit var soundPool: SoundPool

    private var isSoundFeedbackEnabled = true
    private var soundFeedbackVolume = 0f
    private var isHapticFeedbackEnabled = true
    private var hapticFeedbackVibrationStrength = 0
    private var hapticFeedbackVibrationDuration = 0

    private var maxSoundFeedbackVolume = 0
    private var defaultIsSoundFeedbackEnabled = true
    private var defaultSoundFeedbackVolume = 0
    private var defaultIsHapticFeedbackEnabled = true
    private var defaultHapticFeedbackVibrationStrength = 0
    private var defaultHapticFeedbackVibrationDuration = 0

    private var pressSoundId = -1
    private var releaseSoundId = -1
    private var pressStreamId = -1
    private var releaseStreamId = -1
    private var connectionJob: Job? = null
    private var inputStreamObserver: StreamObserver<Gamepad.InputData>? = null

    @FlowPreview
    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ActivityFullscreenBinding.inflate(layoutInflater).apply {
            lifecycleOwner = this@ControllerActivity

            this.vm = this@ControllerActivity.vm
            this.presenter = this@ControllerActivity.presenter

            setContentView(root)

            btnDpadUp.onButtonStateChanged = createButtonStateChangedListener(ButtonType.DPadUp)
            btnDpadDown.onButtonStateChanged = createButtonStateChangedListener(ButtonType.DPadDown)
            btnDpadLeft.onButtonStateChanged = createButtonStateChangedListener(ButtonType.DPadLeft)
            btnDpadRight.onButtonStateChanged =
                createButtonStateChangedListener(ButtonType.DPadRight)
            btnX.onButtonStateChanged = createButtonStateChangedListener(ButtonType.X)
            btnY.onButtonStateChanged = createButtonStateChangedListener(ButtonType.Y)
            btnA.onButtonStateChanged = createButtonStateChangedListener(ButtonType.A)
            btnB.onButtonStateChanged = createButtonStateChangedListener(ButtonType.B)
            btnTriggerLeft.onButtonStateChanged =
                createButtonStateChangedListener(ButtonType.TriggerL)
            btnTriggerRight.onButtonStateChanged =
                createButtonStateChangedListener(ButtonType.TriggerR)
            btnTriggerLeft2.onButtonStateChanged =
                createButtonStateChangedListener(ButtonType.TriggerL2)
            btnTriggerRight2.onButtonStateChanged =
                createButtonStateChangedListener(ButtonType.TriggerR2)
            btnStart.onButtonStateChanged = createButtonStateChangedListener(ButtonType.Start)
            btnSelect.onButtonStateChanged = createButtonStateChangedListener(ButtonType.Select)
            thumbStickLeft.onButtonStateChanged =
                createButtonStateChangedListener(ButtonType.ThumbL)
            thumbStickRight.onButtonStateChanged =
                createButtonStateChangedListener(ButtonType.ThumbR)
        }

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.setOnSystemUiVisibilityChangeListener {
                vm.isSystemUiVisible.value = it and View.SYSTEM_UI_FLAG_FULLSCREEN == 0
            }

            lifecycleScope.launchWhenCreated {
                systemUiVisibilityControlChannel.consumeEach { isVisible ->
                    window.decorView.systemUiVisibility = if (!isVisible) {
                        View.SYSTEM_UI_FLAG_IMMERSIVE or
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_FULLSCREEN
                    } else {
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    }
                }
            }
        } else {
            TODO()
        }

        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()

        defaultIsSoundFeedbackEnabled =
            resources.getBoolean(R.bool.pref_default_enable_sound_feedback)
        defaultSoundFeedbackVolume =
            resources.getInteger(R.integer.pref_default_sound_feedback_volume)
        maxSoundFeedbackVolume = resources.getInteger(R.integer.pref_max_sound_feedback_volume)
        defaultIsHapticFeedbackEnabled =
            resources.getBoolean(R.bool.pref_default_enable_haptic_feedback)
        defaultHapticFeedbackVibrationDuration =
            resources.getInteger(R.integer.pref_default_haptic_feedback_vibration_strength)
        defaultHapticFeedbackVibrationStrength =
            resources.getInteger(R.integer.pref_default_haptic_feedback_vibration_duration)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        pressSoundId = soundPool.load(this, R.raw.press, 1)
        releaseSoundId = soundPool.load(this, R.raw.release, 1)
    }

    override fun onStart() {
        super.onStart()

        val prefMan = PreferenceManager.getDefaultSharedPreferences(this)

        vm.targetAddress.value = prefMan.getString(
            getString(R.string.pref_key_server_address),
            ""
        )

        isSoundFeedbackEnabled = prefMan.getBoolean(
            getString(R.string.pref_key_enable_sound_feedback),
            defaultIsSoundFeedbackEnabled
        )

        soundFeedbackVolume = prefMan.getInt(
            getString(R.string.pref_key_sound_feedback_volume),
            defaultSoundFeedbackVolume
        ) / maxSoundFeedbackVolume.toFloat()

        isHapticFeedbackEnabled = prefMan.getBoolean(
            getString(R.string.pref_key_enable_haptic_feedback),
            defaultIsHapticFeedbackEnabled
        )

        hapticFeedbackVibrationStrength = prefMan.getInt(
            getString(R.string.pref_key_haptic_feedback_vibration_strength),
            defaultHapticFeedbackVibrationStrength
        )

        hapticFeedbackVibrationDuration = prefMan.getInt(
            getString(R.string.pref_key_haptic_feedback_vibration_duration),
            defaultHapticFeedbackVibrationDuration
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            lifecycleScope.launchWhenResumed {
                delay(300)
                systemUiVisibilityControlChannel.send(false)
            }
        }
    }

    private fun createButtonStateChangedListener(buttonType: ButtonType): (ButtonState) -> Unit {
        return {
            when (it) {
                ButtonState.Pressed -> {
                    if (isHapticFeedbackEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val vibrationEffect = VibrationEffect.createOneShot(
                                hapticFeedbackVibrationDuration.toLong(),
                                hapticFeedbackVibrationStrength
                            )
                            vibrator.vibrate(vibrationEffect)
                        } else {
                            @kotlin.Suppress("DEPRECATION")
                            vibrator.vibrate(hapticFeedbackVibrationDuration.toLong())
                        }
                    }

                    if (isSoundFeedbackEnabled) {
                        if (pressStreamId > 1) {
                            soundPool.stop(pressStreamId)
                        }
                        pressStreamId = soundPool.play(
                            pressSoundId,
                            soundFeedbackVolume,
                            soundFeedbackVolume,
                            1,
                            0,
                            1.0f
                        )
                    }
                }
                ButtonState.Released -> if (isSoundFeedbackEnabled) {
                    if (releaseStreamId > 1) {
                        soundPool.stop(releaseStreamId)
                    }
                    releaseStreamId = soundPool.play(
                        releaseSoundId,
                        soundFeedbackVolume,
                        soundFeedbackVolume,
                        1,
                        0,
                        1.0f
                    )
                }
            }

            lifecycleScope.launchWhenStarted {
                withContext(Dispatchers.IO) {
                    inputStreamObserver?.onNext(it.makeEvent(buttonType).toInput())
                }
            }
        }
    }

    private fun connectToServer(targetAddress: String) {
        connectionJob = lifecycleScope.launchWhenCreated {
            vm.state.value = State.Connecting

            val grpcChannel = ManagedChannelBuilder
                .forTarget(targetAddress)
                .usePlaintext()
                .build()

            try {
                withTimeout(10_000) {
                    withContext(Dispatchers.IO) {
                        GamePadGrpc.newBlockingStub(grpcChannel)
                            .withDeadline(Deadline.after(5, TimeUnit.SECONDS))
                            .check(Gamepad.CheckRequest.getDefaultInstance())

                        inputStreamObserver = GamePadGrpc.newStub(grpcChannel)
                            .instantiate(outputStreamObserver)
                    }
                }

                vm.state.value = State.Connected

                waitChannel.receive()
            } catch (_: CancellationException) {
            } catch (_: Exception) {
                Snackbar
                    .make(
                        window.decorView,
                        getString(R.string.snackbar_message_failed_to_connect, targetAddress),
                        Snackbar.LENGTH_LONG
                    )
                    .setAction(R.string.retry) { connectToServer(targetAddress) }
                    .show()
            } finally {
                withContext(NonCancellable) {
                    vm.state.value = State.NotConnected

                    connectionJob = null
                    inputStreamObserver = null

                    grpcChannel.shutdown()
                }
            }
        }
    }

    private val outputStreamObserver = object : StreamObserver<Gamepad.OutputData> {
        override fun onNext(value: Gamepad.OutputData?) {
            when (value?.feedbackCase) {
                Gamepad.OutputData.FeedbackCase.FF_UPLOADED -> {
                    val forceFeedback: ForceFeedback? = when (value.ffUploaded.typeCase) {
                        Gamepad.ForceFeedbackUploaded.TypeCase.RUMBLE -> {
                            ForceFeedback.Rumble(
                                value.ffUploaded.id,
                                value.ffUploaded.replayDelay,
                                value.ffUploaded.replayLength,
                                value.ffUploaded.rumble.strongMagnitude,
                                value.ffUploaded.rumble.weakMagnitude
                            )
                        }
                        else -> null
                    }
                    if (forceFeedback != null) {
                        forceFeedbackMap[forceFeedback.id] = forceFeedback
                    }
                }
                Gamepad.OutputData.FeedbackCase.FF_ERASED -> {
                    forceFeedbackMap.remove(value.ffErased.id)
                }
                Gamepad.OutputData.FeedbackCase.FF_PLAYED -> {
                    forceFeedbackMap[value.ffPlayed.id]?.performVibration(vibrator)
                }
                Gamepad.OutputData.FeedbackCase.FF_STOPPED -> {
                    // stopping the vibration is not really supported
                }
                else -> {
                    // ignore
                }
            }
        }

        override fun onError(t: Throwable?) {
            waitChannel.sendBlocking(Unit)
        }

        override fun onCompleted() {
            waitChannel.sendBlocking(Unit)
        }
    }

    private val presenter = object : Presenter {
        override fun openControllerMenu() {
            MenuDialog().show(supportFragmentManager, null)
        }

        override fun connect() {
            val targetAddress = vm.targetAddress.value
            if (targetAddress.isNullOrBlank()) {
                AlertDialog.Builder(this@ControllerActivity)
                    .setTitle(R.string.dialog_title_server_address_not_set)
                    .setMessage(R.string.dialog_message_server_address_not_set)
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        startActivity(Intent(this@ControllerActivity, SettingsActivity::class.java))

                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.cancel()
                    }
                    .show()
            } else {
                lifecycleScope.launchWhenResumed {
                    connectToServer(targetAddress)
                }
            }
        }

        override fun cancelConnect() {
            connectionJob?.cancel()
        }

        override fun disconnect() {
            inputStreamObserver?.onCompleted()
            waitChannel.sendBlocking(Unit)
        }

        override fun showSystemUi() {
            lifecycleScope.launchWhenResumed {
                systemUiVisibilityControlChannel.send(true)
            }
        }

        override fun hideSystemUi() {
            lifecycleScope.launchWhenResumed {
                systemUiVisibilityControlChannel.send(false)
            }
        }
    }

    interface Presenter {
        fun openControllerMenu()
        fun connect()
        fun cancelConnect()
        fun disconnect()
        fun showSystemUi()
        fun hideSystemUi()
    }

    enum class State {
        NotConnected,
        Connecting,
        Connected,
    }

    class VM : ViewModel() {
        val targetAddress = MutableLiveData("")
        val state = MutableLiveData(State.NotConnected)
        val isSystemUiVisible = MutableLiveData(true)
    }
}