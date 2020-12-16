package com.bmdelacruz.vgp

import android.os.Bundle
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import com.bmdelacruz.vgp.ControllerEvent.Companion.makeEvent
import com.bmdelacruz.vgp.databinding.ActivityFullscreenBinding
import io.grpc.Deadline
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

class ControllerActivity : AppCompatActivity() {
    private val connectChannel = Channel<String>()
    private val cancelConnectChannel = Channel<Unit>()
    private val disconnectChannel = Channel<Unit>()
    private val inputChannel = Channel<Gamepad.InputData>()

    private val vm by viewModels<VM>()

    private lateinit var controllerEventsFlow: Flow<ControllerEvent>

    @FlowPreview
    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityFullscreenBinding.inflate(layoutInflater).apply {
            this.vm = this@ControllerActivity.vm
            lifecycleOwner = this@ControllerActivity
            presenter = object : Presenter {
                override fun openControllerMenu() {
                    // TODO
                }

                override fun connect() {
                    val targetAddress = vm!!.targetAddress.value
                    if (targetAddress.isNullOrBlank()) {
                        val view =
                            layoutInflater.inflate(R.layout.dialog_enter_target_address, null)
                        val txtTargetAddress = view.findViewById<EditText>(R.id.txt_target_address)

                        AlertDialog.Builder(this@ControllerActivity)
                            .setView(view)
                            .setTitle("Enter the server's address")
                            .setMessage("You need to provide the address of the server you want to connect to.")
                            .setPositiveButton("Enter") { dialog, _ ->
                                val enteredTargetAddress = txtTargetAddress.text.toString()

                                lifecycleScope.launchWhenResumed {
                                    connectChannel.send(enteredTargetAddress)
                                }

                                dialog.dismiss()

//                                if (URLUtil.isValidUrl(enteredTargetAddress)) {
//                                    lifecycleScope.launchWhenResumed {
//                                        connectChannel.send(enteredTargetAddress)
//                                    }
//
//                                    dialog.dismiss()
//                                } else {
//                                    Toast
//                                        .makeText(
//                                            this@ControllerActivity,
//                                            "Please enter a valid server address!",
//                                            Toast.LENGTH_SHORT
//                                        )
//                                        .show()
//                                }
                            }
                            .setNegativeButton("Cancel") { dialog, _ ->
                                dialog.cancel()
                            }
                            .show()
                    } else {
                        lifecycleScope.launchWhenResumed {
                            connectChannel.send(targetAddress)
                        }
                    }
                }

                override fun cancelConnect() {
                    lifecycleScope.launchWhenResumed {
                        cancelConnectChannel.send(Unit)
                    }
                }

                override fun disconnect() {
                    lifecycleScope.launchWhenResumed {
                        disconnectChannel.send(Unit)
                    }
                }
            }

            setContentView(root)
        }

        controllerEventsFlow = merge(
            binding.btnDpadUp.stateFlow.map { it.makeEvent(ButtonType.DPadUp) },
            binding.btnDpadDown.stateFlow.map { it.makeEvent(ButtonType.DPadDown) },
            binding.btnDpadLeft.stateFlow.map { it.makeEvent(ButtonType.DPadLeft) },
            binding.btnDpadRight.stateFlow.map { it.makeEvent(ButtonType.DPadRight) },
            binding.btnX.stateFlow.map { it.makeEvent(ButtonType.X) },
            binding.btnY.stateFlow.map { it.makeEvent(ButtonType.Y) },
            binding.btnA.stateFlow.map { it.makeEvent(ButtonType.A) },
            binding.btnB.stateFlow.map { it.makeEvent(ButtonType.B) },
            binding.btnTriggerLeft.stateFlow.map { it.makeEvent(ButtonType.TriggerL) },
            binding.btnTriggerRight.stateFlow.map { it.makeEvent(ButtonType.TriggerR) },
            binding.btnTriggerLeft2.stateFlow.map { it.makeEvent(ButtonType.TriggerL2) },
            binding.btnTriggerRight2.stateFlow.map { it.makeEvent(ButtonType.TriggerR2) },
            binding.btnStart.stateFlow.map { it.makeEvent(ButtonType.Start) },
            binding.btnSelect.stateFlow.map { it.makeEvent(ButtonType.Select) },
            binding.thumbStickLeft.stateFlow.map { it.makeEvent(ButtonType.ThumbL) },
            binding.thumbStickRight.stateFlow.map { it.makeEvent(ButtonType.ThumbR) },
            // TODO: thumb stick position
        ).run {
            shareIn(lifecycleScope, SharingStarted.Eagerly, 0)
        }

        lifecycleScope.launchWhenStarted {
            withContext(Dispatchers.IO) {
                controllerEventsFlow.collect {
                    inputChannel.send(it.toInput())
                }
            }
        }

        lifecycleScope.launchWhenCreated {
            connectChannel
                .consumeAsFlow()
                .flatMapLatest { targetAddress ->
                    val stopFlow = disconnectChannel
                        .receiveAsFlow()
                        .map { true }
                        .onStart { emit(false) }

                    cancelConnectChannel
                        .receiveAsFlow()
                        .map { true }
                        .onStart { emit(false) }
                        .combine(stopFlow) { c, s -> c || s }
                        .distinctUntilChanged()
                        .flatMapLatest {
                            if (it) {
                                flowOf(false)
                            } else {
                                callbackFlow {
                                    val outputChannel = Channel<Gamepad.OutputData>()

                                    val outputStreamObserverCoroutineContext =
                                        CoroutineScope(currentCoroutineContext())

                                    val outputStreamObserver =
                                        object : StreamObserver<Gamepad.OutputData> {
                                            override fun onNext(value: Gamepad.OutputData) {
                                                outputStreamObserverCoroutineContext.launch {
                                                    outputChannel.send(value)
                                                }
                                            }

                                            override fun onError(t: Throwable?) {
                                                outputChannel.close(t)
                                            }

                                            override fun onCompleted() {
                                                outputChannel.close()
                                            }
                                        }

                                    val grpcChannel = ManagedChannelBuilder
                                        .forAddress(targetAddress, 50000)
                                        .usePlaintext()
                                        .build()

                                    awaitClose {
                                        grpcChannel.shutdownNow()
                                    }

                                    GamePadGrpc.newBlockingStub(grpcChannel)
                                        .withDeadline(Deadline.after(5, TimeUnit.SECONDS))
                                        .check(Gamepad.CheckRequest.getDefaultInstance())

                                    val inputStreamObserver = GamePadGrpc.newStub(grpcChannel)
                                        .instantiate(outputStreamObserver)

                                    val inputCollectionJob = launch {
                                        inputChannel
                                            .receiveAsFlow()
                                            .onCompletion {
                                                inputStreamObserver.onCompleted()
                                            }
                                            .collect { input ->
                                                inputStreamObserver.onNext(input)
                                            }
                                    }

                                    awaitClose {
                                        inputCollectionJob.cancel()
                                    }

                                    send(true)

                                    outputChannel.consume { }
                                }.retry(4).catch {
                                    android.util.Log.d(
                                        "ControllerActivity",
                                        "connection error: $it",
                                        it
                                    )
                                }
                            }
                        }
                        .takeWhile { it }
                        .map { State.Connected }
                        .onStart { emit(State.Connecting) }
                        .onCompletion { emit(State.NotConnected) }
                }
                .flowOn(Dispatchers.IO)
                .collect {
                    vm.state.value = it
                }
        }
    }

    interface Presenter {
        fun openControllerMenu()
        fun connect()
        fun cancelConnect()
        fun disconnect()
    }

    enum class State {
        NotConnected,
        Connecting,
        Connected,
    }

    class VM(savedStateHandle: SavedStateHandle) : ViewModel() {
        val targetAddress = savedStateHandle.getLiveData<String>("targetAddress")
        val state = MutableLiveData(State.NotConnected)
    }
}