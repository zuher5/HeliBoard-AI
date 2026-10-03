package helium314.keyboard.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object FeedbackManager {
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun message(context: Context, text: String) {
        _messages.trySend(text)
        mainHandler.post {
            try {
                Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
        }
    }

    fun message(context: Context, resId: Int) {
        message(context, context.getString(resId))
    }
}
