package by.homeunix.tframe.telegram

/**
 * Created by chudsaviet on 2018-03-15.
 */

import org.drinkless.td.libcore.telegram.Client
import org.drinkless.td.libcore.telegram.TdApi
import java.util.concurrent.locks.ReentrantLock
import android.util.Log


class TelegramController(tdLibDirectory:String) {

    private var client:Client
    private var updatesHandler: UpdatesHandler

    val authorizationLock = ReentrantLock()
    var haveAuthorization:Boolean = false
    var aurthorizationState: TdApi.AuthorizationState? = null
    val gotAuthorization = authorizationLock.newCondition()
    var quiting = false

    private val tdLibDirectory: String = tdLibDirectory

    fun onAuthorizationStateUpdated(newAuthorizationState: TdApi.AuthorizationState?) {
        Log.d("AuthorizationState", newAuthorizationState.toString())
        if (newAuthorizationState != null) {
            aurthorizationState = newAuthorizationState

            if (aurthorizationState != null) {
                when (aurthorizationState!!.getConstructor()) {
                    TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                        val parameters: TdApi.TdlibParameters = TdApi.TdlibParameters()
                        parameters.databaseDirectory = tdLibDirectory
                        parameters.useMessageDatabase = false
                        parameters.useSecretChats = false
                        parameters.apiId = 94575
                        parameters.apiHash = "a3406de8d171bb422bb6ddf3bbd800e2"
                        parameters.systemLanguageCode = "en"
                        parameters.deviceModel = "Desktop"
                        parameters.systemVersion = "Unknown"
                        parameters.applicationVersion = "1.0"
                        parameters.enableStorageOptimizer = false
                        parameters.useFileDatabase = false

                        client.send(TdApi.SetTdlibParameters(parameters), AuthorizationRequestHandler());
                    }
                    TdApi.AuthorizationStateWaitEncryptionKey.CONSTRUCTOR ->
                        client.send(TdApi.CheckDatabaseEncryptionKey(), AuthorizationRequestHandler());
                    TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR ->
                        client.send(TdApi.SetAuthenticationPhoneNumber(
                                "+14252745308",
                                false,
                                false)
                                , AuthorizationRequestHandler());
                    TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                        //String code = promptString("Please enter authentication code: ");
                        val code = "2312"
                        client.send(TdApi.CheckAuthenticationCode(code, "F", "G"),
                                AuthorizationRequestHandler());
                    }
                    TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                        val password = "SSS";
                        client.send(TdApi.CheckAuthenticationPassword(password),
                                AuthorizationRequestHandler());
                    }
                    TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                        haveAuthorization = true;
                            authorizationLock.lock();
                        try {
                            gotAuthorization.signal();
                        } finally {
                            authorizationLock.unlock();
                        }
                    }
                    TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR -> haveAuthorization = false
                    TdApi.AuthorizationStateClosing.CONSTRUCTOR -> haveAuthorization = false
                    TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                        if (!quiting) {
                            client = Client.create(UpdatesHandler(), null, null) // recreate client after previous has closed
                        }
                    }
                    else -> {
                        Log.w("AuthorizationState",
                                "Unsupported authorization state: $aurthorizationState")
                    }
                }
            }
        }
    }

    inner class UpdatesHandler: Client.ResultHandler {
        override fun onResult(result: TdApi.Object?) {
            Log.d("UpdatesHandler", result.toString())
            if (result != null) {
                when (result.constructor) {
                    TdApi.UpdateAuthorizationState.CONSTRUCTOR ->
                        onAuthorizationStateUpdated((result as TdApi.UpdateAuthorizationState).authorizationState);
                    else -> '2'
                }
            }
            else throw IllegalArgumentException("Null result received")
        }
    }

    inner class AuthorizationRequestHandler : Client.ResultHandler {
        override fun onResult(result: TdApi.Object) {
            when (result.constructor) {
                TdApi.Error.CONSTRUCTOR -> {
                    System.err.println("Receive an error:\n$result")
                    onAuthorizationStateUpdated(null) // repeat last action
                }
                TdApi.Ok.CONSTRUCTOR -> {
                }
                else -> System.err.println("Receive wrong response from TDLib:\n$result")
            }// result is already received through UpdateAuthorizationState, nothing to do
        }
    }

    init {
        updatesHandler = UpdatesHandler()
        client = Client.create(updatesHandler, null, null)
        while (!quiting) {

            authorizationLock.lock();
            try {
                while (!haveAuthorization) {
                    gotAuthorization.await();
                }
            } finally {
                authorizationLock.unlock();
            }
        }
    }
}

